//
//  BorderFXEffectProxy.m
//  Ti.BorderFX
//
//  Created by Douglas Alves on 05/08/26.
//

#import "BorderFXEffectProxy.h"
#import "TiUtils.h"
#import "TiColor.h"

@implementation BorderFXEffectProxy {
    __weak UIView *_targetView;
    __weak TiViewProxy *_targetProxy;

    CAShapeLayer *_maskLayer;        // desenha o contorno (stroke) e o dash pattern do "beam"
    CALayer *_containerLayer;        // ESTÁTICO — carrega a máscara, nunca é rotacionado
    CAGradientLayer *_gradientLayer; // fornece as cores; sublayer do container, pode girar livremente

    CADisplayLink *_displayLink;
    CGRect _lastBounds;
    CGFloat _lastCornerRadius;
    CGFloat _cachedPerimeter;

    // Movimento é calculado manualmente a cada frame a partir do tempo decorrido —
    // não usa CABasicAnimation, que pode ser descartada quando outras animações
    // (fadeIn/fadeInUp/transform via Ti.UI.Animation) mexem na mesma hierarquia de layers.
    BOOL _isRunning;
    CFTimeInterval _startTimestamp;

    NSString *_mode;       // "beam" | "rotate" | "both"
    CGFloat _borderWidth;
    CGFloat _beamLength;   // fração do perímetro (0..1), usado em "beam"/"both"
    NSTimeInterval _duration; // segundos por volta completa (mesmo valor pra beam e rotate)
}

#pragma mark - Init

- (instancetype)initWithTargetView:(UIView *)view
                        targetProxy:(TiViewProxy *)proxy
                            options:(NSDictionary *)options
                            context:(id<TiEvaluator>)context
{
    self = [super _initWithPageContext:context];
    if (self) {
        _targetView = view;
        _targetProxy = proxy;

        _mode = options[@"mode"] ?: @"both";
        _borderWidth = [TiUtils floatValue:options[@"borderWidth"] def:2.0];
        _beamLength = [TiUtils floatValue:options[@"beamLength"] def:0.25];
        _duration = [TiUtils floatValue:options[@"duration"] def:3000.0] / 1000.0;

        _lastBounds = CGRectNull;
        _lastCornerRadius = -1;
        _cachedPerimeter = 0;
        _isRunning = NO;

        [self setupLayersWithOptions:options];
        [self startDisplayLinkSync];

        // CADisplayLink às vezes não retoma de forma confiável depois de um
        // período suspenso em background — recriamos ele quando o app volta
        // a ficar ativo, em vez de depender do sistema resumir sozinho.
        [[NSNotificationCenter defaultCenter] addObserver:self
                                                  selector:@selector(applicationDidBecomeActive:)
                                                      name:UIApplicationDidBecomeActiveNotification
                                                    object:nil];
    }
    return self;
}

#pragma mark - Setup

- (void)setupLayersWithOptions:(NSDictionary *)options
{
    _maskLayer = [CAShapeLayer layer];
    _maskLayer.fillColor = [UIColor clearColor].CGColor;
    _maskLayer.strokeColor = [UIColor whiteColor].CGColor; // só serve de máscara, a cor real vem do gradiente
    _maskLayer.lineWidth = _borderWidth;
    _maskLayer.lineCap = kCALineCapRound;

    // O container é quem recebe a máscara e NUNCA sofre transform/rotação.
    // É isso que mantém o contorno grudado no lugar certo o tempo todo.
    _containerLayer = [CALayer layer];
    _containerLayer.mask = _maskLayer;

    _gradientLayer = [CAGradientLayer layer];
    if (@available(iOS 12.0, *)) {
        _gradientLayer.type = kCAGradientLayerConic;
    }
    // Para o tipo conic, startPoint define o CENTRO do cone e endPoint o ângulo zero.
    // Sem isso, o padrão (0.5, 0) centraliza o cone no meio-topo da view — errado.
    _gradientLayer.startPoint = CGPointMake(0.5, 0.5);
    _gradientLayer.endPoint = CGPointMake(1.0, 0.5);
    _gradientLayer.colors = [self cgColorsFromArray:options[@"colors"]];

    // O gradiente é FILHO do container (não leva a máscara diretamente) —
    // é ele que gira livremente sem nunca mover o formato da borda.
    [_containerLayer addSublayer:_gradientLayer];
    [_targetView.layer addSublayer:_containerLayer];

    [self syncGeometryIfNeeded]; // aplica frame/path inicial antes do primeiro frame do display link
}

- (NSArray *)cgColorsFromArray:(NSArray *)colorValues
{
    if (![colorValues isKindOfClass:[NSArray class]] || colorValues.count == 0) {
        // fallback: um pequeno arco-íris padrão
        colorValues = @[@"#FF00FF", @"#00FFFF", @"#FFFF00", @"#FF00FF"];
    }
    NSMutableArray *result = [NSMutableArray arrayWithCapacity:colorValues.count];
    for (id value in colorValues) {
        TiColor *tiColor = [TiUtils colorValue:value];
        [result addObject:(id)tiColor.color.CGColor];
    }
    return result;
}

// Lê o borderRadius que VOCÊ configurou na view (na proxy Titanium), não o que
// terminou (ou não) na layer nativa. TiProxy guarda todo valor setado via JS
// (view.borderRadius = X) na sua tabela de propriedades interna, recuperável
// por KVC — isso é mais confiável do que view.layer.cornerRadius, porque o
// Titanium às vezes desenha o cantoarredondado via uma camada de background
// própria dele, sem nunca tocar em layer.cornerRadius.
- (CGFloat)resolvedCornerRadius
{
    id storedValue = [_targetProxy valueForKey:@"borderRadius"];
    if (storedValue != nil) {
        return [TiUtils floatValue:storedValue def:0];
    }
    return _targetView.layer.cornerRadius; // fallback, caso borderRadius nunca tenha sido setado
}

#pragma mark - Display link tick

- (void)onDisplayLinkTick
{
    [self syncGeometryIfNeeded];
    [self updateMotion];
}

- (void)startDisplayLinkSync
{
    _displayLink = [CADisplayLink displayLinkWithTarget:self selector:@selector(onDisplayLinkTick)];
    [_displayLink addToRunLoop:[NSRunLoop mainRunLoop] forMode:NSRunLoopCommonModes];
}

- (void)applicationDidBecomeActive:(NSNotification *)notification
{
    TiThreadPerformOnMainThread(^{
        [self->_displayLink invalidate];
        self->_displayLink = nil;
        [self startDisplayLinkSync];
    }, NO);
}

#pragma mark - Geometry sync (frame/path — só recalcula quando bounds/cornerRadius mudam)

- (void)syncGeometryIfNeeded
{
    UIView *view = _targetView;
    if (view == nil) {
        return; // view alvo já foi liberada; destroy: deveria ter sido chamado
    }

    // Durante uma animação Core Animation (ex: view.animate() do Titanium), o
    // valor "model" de bounds já pula pro tamanho FINAL na hora — só a
    // presentation layer mostra o valor interpolado, ao vivo, quadro a quadro.
    // Sem isso, nossa borda salta pro tamanho final instantaneamente enquanto
    // a view visível ainda está no meio da transição suave.
    CALayer *presentationLayer = view.layer.presentationLayer;
    CGRect bounds = presentationLayer ? presentationLayer.bounds : view.bounds;
    CGFloat cornerRadius = [self resolvedCornerRadius];

    BOOL boundsChanged = !CGRectEqualToRect(bounds, _lastBounds);
    BOOL radiusChanged = (cornerRadius != _lastCornerRadius);

    if (!boundsChanged && !radiusChanged) {
        return; // nada mudou — não recalcula path a cada um dos 60fps à toa
    }

    _lastBounds = bounds;
    _lastCornerRadius = cornerRadius;

    [CATransaction begin];
    [CATransaction setDisableActions:YES]; // sem animação implícita ao reposicionar

    _containerLayer.frame = bounds;
    _maskLayer.frame = bounds; // mesma origem/tamanho do container — coordenadas locais batem certinho

    CGRect pathRect = CGRectInset(bounds, _borderWidth / 2.0, _borderWidth / 2.0);
    // Ao encolher o retângulo pelo inset, o raio do canto tem que encolher junto
    // (mesma lógica de border-box -> padding-box do CSS). Manter o raio original
    // num retângulo menor deforma o canto — e a deformação cresce com borderWidth,
    // exatamente o sintoma reportado.
    CGFloat pathCornerRadius = MAX(cornerRadius - (_borderWidth / 2.0), 0);
    UIBezierPath *path = [UIBezierPath bezierPathWithRoundedRect:pathRect cornerRadius:pathCornerRadius];
    _maskLayer.path = path.CGPath;

    // O gradiente é maior que o card (lado = diagonal) e centralizado, para que
    // ao girar ele nunca "descubra" um canto vazio nem deixe o container sem cobertura.
    // IMPORTANTE: setamos bounds+position em vez de .frame — a _gradientLayer já
    // tem um .transform ativo (a rotação), e escrever .frame numa layer com
    // transform não-identidade é comportamento indefinido pela documentação da
    // Apple. Na prática isso causava o anel "quebrar" durante resize simultâneo
    // com a rotação.
    CGFloat diagonal = hypot(bounds.size.width, bounds.size.height);
    _gradientLayer.bounds = CGRectMake(0, 0, diagonal, diagonal);
    _gradientLayer.position = CGPointMake(bounds.size.width / 2.0, bounds.size.height / 2.0);

    _cachedPerimeter = [self perimeterForRect:pathRect cornerRadius:pathCornerRadius];

    if ([_mode isEqualToString:@"rotate"]) {
        _maskLayer.lineDashPattern = nil; // anel sólido — a cor "anda" via rotação do gradiente, não do dash
    } else {
        CGFloat dashLength = MAX(_cachedPerimeter * _beamLength, 1.0);
        _maskLayer.lineDashPattern = @[@(dashLength), @(MAX(_cachedPerimeter - dashLength, 1.0))];
    }

    [CATransaction commit];
}

// Perímetro exato de um retângulo arredondado: 2 lados retos + arco de 360° dividido em 4 cantos
- (CGFloat)perimeterForRect:(CGRect)rect cornerRadius:(CGFloat)radius
{
    CGFloat w = rect.size.width;
    CGFloat h = rect.size.height;
    CGFloat r = MIN(radius, MIN(w, h) / 2.0);
    return 2 * (w - 2 * r) + 2 * (h - 2 * r) + 2 * M_PI * r;
}

#pragma mark - Motion (calculado manualmente a cada frame, sem CAAnimation)

- (void)updateMotion
{
    if (!_isRunning || _cachedPerimeter <= 0) {
        return;
    }

    CFTimeInterval elapsed = CACurrentMediaTime() - _startTimestamp;
    CGFloat progress = fmod(elapsed / _duration, 1.0); // 0..1 dentro da volta atual

    [CATransaction begin];
    [CATransaction setDisableActions:YES];

    if (![_mode isEqualToString:@"rotate"]) {
        _maskLayer.lineDashPhase = -progress * _cachedPerimeter;
    }
    if ([_mode isEqualToString:@"rotate"] || [_mode isEqualToString:@"both"]) {
        _gradientLayer.transform = CATransform3DMakeRotation(progress * 2.0 * M_PI, 0, 0, 1);
    }

    [CATransaction commit];
}

#pragma mark - JS API

- (void)start:(id)args
{
    TiThreadPerformOnMainThread(^{
        self->_containerLayer.hidden = NO;
        self->_startTimestamp = CACurrentMediaTime();
        self->_isRunning = YES;
    }, NO);
}

- (void)stop:(id)args
{
    TiThreadPerformOnMainThread(^{
        self->_isRunning = NO; // congela no frame atual, sem "voltar" pro início
    }, NO);
}

- (void)updateColors:(id)args
{
    ENSURE_ARG_COUNT(args, 1);
    NSArray *colors = [args objectAtIndex:0];
    TiThreadPerformOnMainThread(^{
        self->_gradientLayer.colors = [self cgColorsFromArray:colors];
    }, NO);
}

- (void)destroy:(id)args
{
    [[NSNotificationCenter defaultCenter] removeObserver:self];
    TiThreadPerformOnMainThread(^{
        self->_isRunning = NO;
        [self->_displayLink invalidate];
        self->_displayLink = nil;
        [self->_containerLayer removeFromSuperlayer];
        self->_containerLayer = nil;
        self->_gradientLayer = nil;
        self->_maskLayer = nil;
    }, NO);
}

- (void)dealloc
{
    [_displayLink invalidate];
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

@end

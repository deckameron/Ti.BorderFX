/**
 * Ti.BorderFX
 *
 * Created by Douglas Alves
 * Copyright (c) 2026 Your Company. All rights reserved.
 */


#import "TiProxy.h"
#import "TiViewProxy.h"

@interface BorderFXEffectProxy : TiProxy

- (instancetype)initWithTargetView:(UIView *)view
                        targetProxy:(TiViewProxy *)proxy
                            options:(NSDictionary *)options
                            context:(id<TiEvaluator>)context;

// Métodos expostos ao JS
- (void)start:(id)args;
- (void)stop:(id)args;
- (void)destroy:(id)args;
- (void)updateColors:(id)args;

@end

/**
 * Ti.BorderFX
 *
 * Created by Douglas Alves
 * Copyright (c) 2026 Your Company. All rights reserved.
 */

#import "TiModule.h"

@interface TiBorderfxModule : TiModule {

}

/**
 * JS: BorderFX.attach(view, { mode, colors, borderWidth, beamLength, duration })
 * Retorna um BorderFXEffectProxy que controla o efeito anexado à view.
 */
- (id)attach:(id)args;

@end

/**
 * Ti.BorderFX
 *
 * Created by Douglas Alves
 * Copyright (c) 2026 Your Company. All rights reserved.
 */

#import "TiBorderfxModule.h"
#import "TiBase.h"
#import "TiHost.h"
#import "TiUtils.h"
#import "TiViewProxy.h"
#import "BorderFXEffectProxy.h"


@implementation TiBorderfxModule

#pragma mark Internal

// This is generated for your module, please do not change it
- (id)moduleGUID
{
  return @"d805b337-e552-4c8b-ad51-a2e66b86d0f3";
}

// This is generated for your module, please do not change it
- (NSString *)moduleId
{
  return @"ti.borderfx";
}

#pragma mark Lifecycle

- (void)startup
{
  // This method is called when the module is first loaded
  // You *must* call the superclass
  [super startup];
  DebugLog(@"[DEBUG] %@ loaded", self);
}

#pragma Public APIs

- (id)attach:(id)args
{
    ENSURE_ARG_COUNT(args, 1);
 
    TiViewProxy *targetProxy = [args objectAtIndex:0];
    NSDictionary *options = ([args count] > 1) ? [args objectAtIndex:1] : @{};
 
    ENSURE_TYPE(targetProxy, TiViewProxy);
    ENSURE_TYPE_OR_NIL(options, NSDictionary);
 
    __block BorderFXEffectProxy *effectProxy = nil;
 
    // Força a criação/obtenção da UIView nativa por trás do proxy Titanium.
    // Precisa rodar na main thread, como qualquer manipulação de UIKit.
    TiThreadPerformOnMainThread(^{
        UIView *nativeView = [targetProxy view];
        effectProxy = [[BorderFXEffectProxy alloc] initWithTargetView:nativeView
                                                            targetProxy:targetProxy
                                                                options:options
                                                                context:[self pageContext]];
    }, YES);
 
    return effectProxy;
}

@end

//
//  AppDelegate.m
//  Iyan3D
//
//  Created by Harishankar Narayanan on 03/01/14.
//  Copyright (c) 2014 Smackall Games. All rights reserved.
//

#import "AppDelegate.h"

#include "SGEngineCommon.h"
#include "SGEngineOGL.h"
#include "SGEngineMTL.h"
#include <GoogleSignIn/GoogleSignIn.h>
#include "Utility.h"
#include <sys/types.h>
#include <sys/sysctl.h>
#include <mach/machine.h>
#import <Fabric/Fabric.h>
#import <TwitterKit/TwitterKit.h>
#import <Crashlytics/Crashlytics.h>


SceneManager *scenemgr;
@interface AppDelegate ()

@property(nonatomic, assign) BOOL okToWait;
@property(nonatomic, copy) void (^dispatchHandler)(GAIDispatchResult result);

@end

static NSString *const kTrackingId = @"UA-49819146-1";
static NSString *const kAllowTracking = @"allowTracking";
static NSString * const kClient = @"328259754555-buqbocp0ehq7mtflh0lk3j2p82cc4ltm.apps.googleusercontent.com";

@implementation AppDelegate

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {

    [[Twitter sharedInstance] startWithConsumerKey:@"FVYtYJI6e4lZMHoZvYCt2ejao" consumerSecret:@"eiFIXzb9zjoaH0lrDZ2Jrh2ezvbmuFv6rvPJdIXLYxgkaZ7YKC"];
    
#if !(TARGET_IPHONE_SIMULATOR)
    [Fabric with:@[[Crashlytics class], [Twitter class]]];
#else
    [Fabric with:@[[Twitter class]]];
#endif
    
    [GIDSignIn sharedInstance].clientID = kClient;

    NSDictionary *appDefaults = @{kAllowTracking: @(YES)};
    [[NSUserDefaults standardUserDefaults] registerDefaults:appDefaults];
    [GAI sharedInstance].optOut = ![[NSUserDefaults standardUserDefaults] boolForKey:kAllowTracking];
    [GAI sharedInstance].dispatchInterval = 20;
    [GAI sharedInstance].trackUncaughtExceptions = YES;
    //[[GAI sharedInstance].logger setLogLevel:kGAILogLevelVerbose];
    //    self.tracker = [[GAI sharedInstance] trackerWithName:@"CuteAnimals"
    //                                              trackingId:kTrackingId];
    self.tracker = [[GAI sharedInstance] trackerWithTrackingId:kTrackingId];
    //self.tracker.allowIDFACollection = NO;

    NSArray* srcDirPath = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString* docDirPath = [srcDirPath objectAtIndex:0];
    NSLog(@"doc dir %@",docDirPath);
    [[UIApplication sharedApplication] setStatusBarStyle:UIStatusBarStyleLightContent];
    self.window = [[UIWindow alloc] initWithFrame:[[UIScreen mainScreen] bounds]];
    if([Utility IsPadDevice]){
        [[UIApplication sharedApplication] setStatusBarHidden:NO];
		loadingViewController = [[LoadingViewControllerPad alloc] initWithNibName:@"LoadingViewControllerPad" bundle:nil];
    }
    else {
        [[UIApplication sharedApplication] setStatusBarHidden:YES];
        loadingViewController = [[LoadingViewControllerPad alloc] initWithNibName:([self iPhone6Plus]) ? @"LoadingViewControllerPhone@2x" : @"LoadingViewControllerPhone" bundle:nil];
    }
    [self.window setRootViewController:loadingViewController];
    [self.window makeKeyAndVisible];
    
    return YES;
}
-(void) initEngine:(int)type ScreenWidth:(float)width ScreenHeight:(float)height ScreenScale:(float)screenScale renderView:(UIView*) view
{
    scenemgr = new SceneManager(width,height,screenScale,(DEVICE_TYPE)type,[[[NSBundle mainBundle] resourcePath] UTF8String],(__bridge void*)view);
}
+(AppDelegate *)getAppDelegate
{
    return (AppDelegate*)[UIApplication sharedApplication].delegate;
}
-(void*) getSceneManager{
    return scenemgr;
}

-(bool) isMetalSupported
{
    size_t size;
    cpu_type_t type;
    cpu_subtype_t subtype;
    size = sizeof(type);
    sysctlbyname("hw.cputype", &type, &size, NULL, 0);
    
    size = sizeof(subtype);
    sysctlbyname("hw.cpusubtype", &subtype, &size, NULL, 0);
    if(subtype == CPU_SUBTYPE_ARM64_V8)
        return true;
    return false;
}
- (void)applicationWillResignActive:(UIApplication *)application {
    [[NSNotificationCenter defaultCenter] postNotificationName:@"AppGoneBackground" object:nil userInfo:nil];
}

- (void)applicationDidEnterBackground:(UIApplication *)application {
    [self sendHitsInBackground];
}

- (void)applicationWillEnterForeground:(UIApplication *)application {
}

- (void)applicationDidBecomeActive:(UIApplication *)application {
    [GAI sharedInstance].optOut = ![[NSUserDefaults standardUserDefaults] boolForKey:kAllowTracking];
    [[NSNotificationCenter defaultCenter] postNotificationName:@"applicationDidBecomeActive" object:nil userInfo:nil];
}

- (void)sendHitsInBackground {
    self.okToWait = YES;
    __weak AppDelegate *weakSelf = self;
    __block UIBackgroundTaskIdentifier backgroundTaskId =
    [[UIApplication sharedApplication] beginBackgroundTaskWithExpirationHandler:^{
        weakSelf.okToWait = NO;
    }];
    
    if (backgroundTaskId == UIBackgroundTaskInvalid) {
        return;
    }
    
    self.dispatchHandler = ^(GAIDispatchResult result) {
        // If the last dispatch succeeded, and we're still OK to stay in the background then kick off
        // again.
        if (result == kGAIDispatchGood && weakSelf.okToWait ) {
            [[GAI sharedInstance] dispatchWithCompletionHandler:weakSelf.dispatchHandler];
        } else {
            [[UIApplication sharedApplication] endBackgroundTask:backgroundTaskId];
        }
    };
    [[GAI sharedInstance] dispatchWithCompletionHandler:self.dispatchHandler];
}

- (void)applicationWillTerminate:(UIApplication *)application {
}
- (BOOL)application:(UIApplication *)application openURL:(NSURL *)url sourceApplication:(NSString *)sourceApplication annotation:(id)annotation{
	NSString* ext = [[url absoluteString] pathExtension];
    NSString* msg;
    
    if([ext isEqualToString:@"ttf"] || [ext isEqualToString:@"otf"])
        msg = [NSString stringWithFormat:@"To use your %@ font file, use 'Text' option in your Import Menu. You should have upgraded to the Premium version for using this feature.", [ext uppercaseString]];
    else if([ext isEqualToString:@"obj"] || [ext isEqualToString:@"png"]|| [ext isEqualToString:@"jpeg"])
    {
         msg = [NSString stringWithFormat:@"To import your %@ file, use 'Import And Rig Models' button in Home Page", [ext uppercaseString]];
    }
    else
    {
        return [[FBSDKApplicationDelegate sharedInstance] application:application
                                                              openURL:url
                                                    sourceApplication:sourceApplication
                                                           annotation:annotation];
    }
    
    [self.window endEditing:YES];
//	UIAlertView *message = [[UIAlertView alloc]initWithTitle:@"Information" message:msg delegate:self cancelButtonTitle:@"Ok" otherButtonTitles:nil];
	//[message performSelectorOnMainThread:@selector(show) withObject:nil waitUntilDone:YES];

	return YES;
}

-(BOOL)iPhone6Plus{
    if (([UIScreen mainScreen].scale > 2.0)) return YES;
    return NO;
}

@end

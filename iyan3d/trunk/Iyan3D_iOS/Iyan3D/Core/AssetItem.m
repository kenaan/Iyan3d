//
//  AssetItem.m
//  Iyan3D
//
//  Created by Harishankar Narayanan on 03/01/14.
//  Copyright (c) 2014 Smackall Games. All rights reserved.
//

#import "AssetItem.h"

@implementation AssetItem

@synthesize hash = _hash;

-(void) dealloc{
	_name = nil;
	_iap = nil;
	_hash = nil;
	_modifiedDate = nil;
	_keywords = nil;
    _price = nil;
    _isTempAsset = false;
}
@end

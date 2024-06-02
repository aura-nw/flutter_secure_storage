//
//  UseBioMetric.swift
//  flutter_secure_storage
//
//  Created by Nguyen Van Toan on 14/08/2023.
//

import Foundation
import LocalAuthentication

class FlutterSecureStorageUseBioMetric{
    
    static func write(query : Dictionary<CFString, Any> , attrAccessible : CFString , keyExists : Bool , synchronizable : Bool? ,value: String , useBioMetric : FlutterSecureStorageUseBioMetric.UseBiometric) -> OSStatus {
        
        var keyChainQuery : Dictionary<CFString,Any> = [:].merging(query){ first , last in
            return last
        }
        
        if (keyExists) {
                var update: [CFString: Any?] = [
                    kSecValueData: value.data(using: String.Encoding.utf8),
                    kSecAttrSynchronizable: synchronizable
                ]
                
                return SecItemUpdate(keyChainQuery as CFDictionary, update as CFDictionary)
            } else {
                keyChainQuery[kSecValueData] = value.data(using: String.Encoding.utf8)
                
                let context = LAContext()
                
                context.localizedReason = useBioMetric.localizedReason
                
                
                if let userAuthenticationTimeout = useBioMetric.userAuthenticationTimeout{
                    
                    if let touchIDAuthenticationAllowableReuseDuration = Double(userAuthenticationTimeout){
                        context.touchIDAuthenticationAllowableReuseDuration = touchIDAuthenticationAllowableReuseDuration
                    }
                    
                }
                
                let access = SecAccessControlCreateWithFlags(nil,
                                                             attrAccessible,
                                                             .userPresence,
                                                             nil)
                
                keyChainQuery[kSecUseAuthenticationContext] = context
                
                keyChainQuery[kSecAttrAccessControl] = access
                
                return SecItemAdd(keyChainQuery as CFDictionary, nil)
            }
    }
    
    class UseBiometric : Decodable{
        var userAuthenticationTimeout: String?
        var localizedReason : String
        
        enum CodingKeys: String, CodingKey {
            case userAuthenticationTimeout = "userAuthenticationTimeout"
            case localizedReason = "localizedReason"
        }
        
        required init(from decoder: Decoder) throws {
            let container: KeyedDecodingContainer<UseBiometric.CodingKeys> = try decoder.container(keyedBy: CodingKeys.self)
            self.userAuthenticationTimeout = try container.decodeIfPresent(String.self, forKey: CodingKeys.userAuthenticationTimeout)
            self.localizedReason = try container.decode(String.self, forKey: CodingKeys.localizedReason)
        }
    }
}

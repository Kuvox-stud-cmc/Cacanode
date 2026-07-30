import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Linking from 'expo-linking';

import { env } from '@/constants/env';
import type { ApiError } from '@/services/api/errors';

const KEY='cacanode.mobile.role-unsupported';
export function isMobileRoleUnsupported(error:Partial<ApiError>):boolean{return error.status===403&&(error.code==='MOBILE_ROLE_UNSUPPORTED'||error.message==='MOBILE_ROLE_UNSUPPORTED');}
export async function rememberUnsupportedRole():Promise<void>{await AsyncStorage.setItem(KEY,'true');}
export async function clearUnsupportedRole():Promise<void>{await AsyncStorage.removeItem(KEY);}
export async function hasUnsupportedRole():Promise<boolean>{return (await AsyncStorage.getItem(KEY))==='true';}
export async function openPlatformAdministration():Promise<void>{await Linking.openURL(`${env.webAppUrl}/platform`);}

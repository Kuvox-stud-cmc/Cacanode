import { publicConfig } from "@/lib/public-config";

export function localizedPath(locale:string,path:string){return `${publicConfig.siteUrl.replace(/\/$/,"")}${locale==="vi"?"/vi":""}${path}`;}
export function languageAlternates(path:string){return {en:localizedPath("en",path),vi:localizedPath("vi",path),"x-default":localizedPath("en",path)};}

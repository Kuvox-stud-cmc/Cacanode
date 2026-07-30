import { useState } from 'react';
import { StyleSheet,View } from 'react-native';
import { WebView, type WebViewMessageEvent } from 'react-native-webview';

import { AppText } from '@/components/ui/app-text';
import { spacing } from '@/constants/theme';

export function RestrictedRichTextEditor({label,value,onChange}:{label:string;value:string;onChange:(html:string)=>void}){
  const[source]=useState(()=>({html:`<!doctype html><meta name="viewport" content="width=device-width"><style>body{font-family:system-ui;margin:12px;color:#0f172a}#editor{min-height:180px;outline:none}</style><div id="editor" contenteditable="true"></div><script>const e=document.getElementById('editor');e.innerHTML=${JSON.stringify(value).replaceAll('<','\\u003c')};e.addEventListener('input',()=>window.ReactNativeWebView.postMessage(e.innerHTML));</script>`}));
  const message=(event:WebViewMessageEvent)=>onChange(event.nativeEvent.data);
  return <View style={styles.container}><AppText variant="bodySmall">{label}</AppText><WebView allowFileAccess={false} allowUniversalAccessFromFileURLs={false} javaScriptCanOpenWindowsAutomatically={false} mixedContentMode="never" onMessage={message} onShouldStartLoadWithRequest={request=>request.url==='about:blank'} originWhitelist={['about:blank']} setSupportMultipleWindows={false} source={source} style={styles.editor}/><AppText muted variant="caption">{value.length.toLocaleString()} chars</AppText></View>;
}
const styles=StyleSheet.create({container:{gap:spacing.xs},editor:{height:220,borderRadius:12}});

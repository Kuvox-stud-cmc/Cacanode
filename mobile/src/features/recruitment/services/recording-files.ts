import { createAudioPlayer } from 'expo-audio';
import * as Crypto from 'expo-crypto';
import { File,Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';

import { env } from '@/constants/env';
import { accessTokenStore } from '@/services/auth/access-token-store';

function recordingUrl(interviewId:string,recordingId:string,download=false){return `${env.apiBaseUrl}/recruitment/interviews/${encodeURIComponent(interviewId)}/recordings/${encodeURIComponent(recordingId)}/${download?'download':'playback'}`;}
export async function cacheInterviewRecording(interviewId:string,recordingId:string){
  const response=await fetch(recordingUrl(interviewId,recordingId),{headers:{Authorization:`Bearer ${accessTokenStore.get()??''}`}});
  if(!response.ok)throw new Error('Recording is not available');
  const file=new File(Paths.cache,`recruitment-recording-${Crypto.randomUUID()}.audio`);file.create({overwrite:true});file.write(new Uint8Array(await response.arrayBuffer()));
  return {uri:file.uri,contentType:response.headers.get('content-type')??'audio/mpeg',cleanup:()=>{try{if(file.exists)file.delete()}catch{}}};
}
export async function createCachedRecordingPlayer(interviewId:string,recordingId:string){const cached=await cacheInterviewRecording(interviewId,recordingId);const player=createAudioPlayer(cached.uri);return {player,uri:cached.uri,cleanup:()=>{try{player.remove()}finally{cached.cleanup()}}};}
export async function shareInterviewRecording(interviewId:string,recordingId:string){const cached=await cacheInterviewRecording(interviewId,recordingId);try{if(!await Sharing.isAvailableAsync())throw new Error('Sharing is not available');await Sharing.shareAsync(cached.uri,{mimeType:cached.contentType});}finally{cached.cleanup()}}

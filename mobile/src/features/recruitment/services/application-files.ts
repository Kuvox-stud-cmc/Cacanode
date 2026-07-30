import * as Crypto from 'expo-crypto';
import { File,Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { env } from '@/constants/env';
import { accessTokenStore } from '@/services/auth/access-token-store';

export async function shareApplicationCv(applicationId:string){
  const response=await fetch(`${env.apiBaseUrl}/recruitment/applications/${encodeURIComponent(applicationId)}/cv`,{headers:{Authorization:`Bearer ${accessTokenStore.get()??''}`}});
  if(!response.ok)throw new Error('CV is not available');
  const contentType=response.headers.get('content-type')??'application/pdf';const extension=contentType.includes('pdf')?'pdf':'bin';const file=new File(Paths.cache,`candidate-cv-${Crypto.randomUUID()}.${extension}`);
  file.create({overwrite:true});file.write(new Uint8Array(await response.arrayBuffer()));
  try{if(!await Sharing.isAvailableAsync())throw new Error('Sharing is not available');await Sharing.shareAsync(file.uri,{mimeType:contentType,dialogTitle:'Candidate CV'});}finally{try{if(file.exists)file.delete()}catch{}}
}

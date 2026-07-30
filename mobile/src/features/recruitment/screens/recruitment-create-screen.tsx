import { useRouter } from 'expo-router';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { StyleSheet,View } from 'react-native';

import { KeyboardScreen } from '@/components/layout/screen';
import { AppText } from '@/components/ui/app-text';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { TextField } from '@/components/ui/text-field';
import { spacing } from '@/constants/theme';
import { useCreateRecruitmentApplicationMutation,useCreateRecruitmentCandidateMutation,useCreateRecruitmentJobMutation,useCreateRecruitmentTemplateMutation,useGetRecruitmentCandidatesQuery,useGetRecruitmentJobsQuery } from '@/features/recruitment/api/recruitment-api';
import { ChoiceField } from '@/features/recruitment/components/choice-field';
import { emptyJob,emptyTemplate,initialContent,validateJob,validateTemplate } from '@/features/recruitment/model/form-model';
import { JobEditor } from '@/features/recruitment/screens/recruitment-item-screen';
import type { CandidateWrite,JobWrite,TemplateCreate } from '@/features/recruitment/types';

export type RecruitmentCreateKind='job'|'candidate'|'application'|'template';
export function RecruitmentCreateScreen({kind}:{kind:RecruitmentCreateKind}){
  const {t}=useTranslation();
  return <KeyboardScreen contentContainerStyle={styles.content}><AppText accessibilityRole="header" variant="title">{t(`recruitment.create.${kind}`)}</AppText>{kind==='job'?<JobCreate/>:kind==='candidate'?<CandidateCreate/>:kind==='application'?<ApplicationCreate/>:<TemplateCreateForm/>}</KeyboardScreen>;
}

function FormError({message}:{message:string}){return message?<AppText accessibilityRole="alert" style={styles.error}>{message}</AppText>:null}
function JobCreate(){
  const {t}=useTranslation();const router=useRouter();const[value,setValue]=useState<JobWrite>(emptyJob());const[error,setError]=useState('');const[create,{isLoading}]=useCreateRecruitmentJobMutation();
  const submit=async()=>{const invalid=validateJob(value);if(invalid){setError(t(`recruitment.validation.${invalid}`));return}try{const saved=await create(value).unwrap();router.replace({pathname:'/(app)/recruitment/item/[kind]/[id]',params:{kind:'job',id:saved.id}} as never)}catch{setError(t('recruitment.saveFailed'))}};
  return <View style={styles.stack}><JobEditor value={value} onChange={setValue}/><FormError message={error}/><Button loading={isLoading} onPress={()=>void submit()}>{t('common.save')}</Button></View>;
}
function CandidateCreate(){
  const {t}=useTranslation();const router=useRouter();const[value,setValue]=useState<CandidateWrite>({fullName:'',email:'',phone:null,notes:null});const[error,setError]=useState('');const[create,{isLoading}]=useCreateRecruitmentCandidateMutation();
  const submit=async()=>{if(!value.fullName.trim()||!/^\S+@\S+\.\S+$/.test(value.email)){setError(t('recruitment.validation.candidate'));return}try{const saved=await create(value).unwrap();router.replace({pathname:'/(app)/recruitment/item/[kind]/[id]',params:{kind:'candidate',id:saved.id}} as never)}catch{setError(t('recruitment.saveFailed'))}};
  return <View style={styles.stack}><CandidateFields value={value} onChange={setValue}/><FormError message={error}/><Button loading={isLoading} onPress={()=>void submit()}>{t('common.save')}</Button></View>;
}
export function CandidateFields({value,onChange,disabled=false}:{value:CandidateWrite;onChange:(value:CandidateWrite)=>void;disabled?:boolean}){const{t}=useTranslation();return <Card style={styles.stack}><TextField editable={!disabled} label={t('recruitment.fields.fullName')} value={value.fullName} onChangeText={fullName=>onChange({...value,fullName})}/><TextField autoCapitalize="none" editable={!disabled} keyboardType="email-address" label={t('recruitment.fields.email')} value={value.email} onChangeText={email=>onChange({...value,email})}/><TextField editable={!disabled} keyboardType="phone-pad" label={t('recruitment.fields.phone')} value={value.phone??''} onChangeText={phone=>onChange({...value,phone:phone||null})}/><TextField editable={!disabled} label={t('recruitment.fields.notes')} multiline value={value.notes??''} onChangeText={notes=>onChange({...value,notes:notes||null})}/></Card>}
function ApplicationCreate(){
  const {t}=useTranslation();const router=useRouter();const jobs=useGetRecruitmentJobsQuery();const candidates=useGetRecruitmentCandidatesQuery();const[jobId,setJobId]=useState('');const[candidateId,setCandidateId]=useState('');const[error,setError]=useState('');const[create,{isLoading}]=useCreateRecruitmentApplicationMutation();
  const submit=async()=>{if(!jobId||!candidateId){setError(t('recruitment.validation.application'));return}try{const saved=await create({jobId,candidateId}).unwrap();router.replace({pathname:'/(app)/recruitment/item/[kind]/[id]',params:{kind:'application',id:saved.id}} as never)}catch{setError(t('recruitment.saveFailed'))}};
  return <View style={styles.stack}><Card style={styles.stack}><ChoiceField label={t('recruitment.fields.job')} value={jobId} choices={(jobs.data??[]).filter(item=>!['ARCHIVED','CLOSED'].includes(item.status)).map(item=>({label:`${item.title} · ${item.status}`,value:item.id}))} onChange={setJobId}/><ChoiceField label={t('recruitment.fields.candidate')} value={candidateId} choices={(candidates.data??[]).map(item=>({label:`${item.fullName} · ${item.email}`,value:item.id}))} onChange={setCandidateId}/></Card><FormError message={error}/><Button loading={isLoading||jobs.isLoading||candidates.isLoading} onPress={()=>void submit()}>{t('recruitment.create.application')}</Button></View>;
}
function TemplateCreateForm(){
  const {t}=useTranslation();const router=useRouter();const[value,setValue]=useState<TemplateCreate>(emptyTemplate());const[error,setError]=useState('');const[create,{isLoading}]=useCreateRecruitmentTemplateMutation();const content=value.content;const section=content.sections[0];const question=section.questions[0];
  const submit=async()=>{const invalid=validateTemplate(value);if(invalid){setError(t(`recruitment.validation.${invalid}`));return}try{const saved=await create(value).unwrap();router.replace({pathname:'/(app)/recruitment/item/[kind]/[id]',params:{kind:'template',id:saved.id}} as never)}catch{setError(t('recruitment.saveFailed'))}};
  return <View style={styles.stack}><Card style={styles.stack}><TextField label={t('recruitment.fields.name')} value={value.name} onChangeText={name=>setValue({...value,name})}/><TextField label={t('recruitment.fields.description')} multiline value={value.description??''} onChangeText={description=>setValue({...value,description:description||null})}/><ChoiceField label={t('recruitment.fields.language')} value={value.locale} choices={[{label:'English',value:'en-US'},{label:'Tiếng Việt',value:'vi-VN'}]} onChange={locale=>setValue({...value,locale:locale as TemplateCreate['locale'],content:initialContent(locale as TemplateCreate['locale'])})}/><TextField label={t('recruitment.fields.introduction')} multiline value={content.introductionText} onChangeText={introductionText=>setValue({...value,content:{...content,introductionText}})}/><TextField label={t('recruitment.fields.disclosure')} multiline value={content.disclosureText} onChangeText={disclosureText=>setValue({...value,content:{...content,disclosureText}})}/><TextField label={t('recruitment.fields.closing')} multiline value={content.closingText} onChangeText={closingText=>setValue({...value,content:{...content,closingText}})}/><TextField keyboardType="number-pad" label={t('recruitment.fields.durationSeconds')} value={String(content.durationLimitSeconds)} onChangeText={durationLimitSeconds=>setValue({...value,content:{...content,durationLimitSeconds:Number(durationLimitSeconds)||0}})}/><AppText variant="heading">{t('recruitment.template.firstSection')}</AppText><TextField label={t('recruitment.fields.transition')} value={section.transitionText??''} onChangeText={transitionText=>setValue({...value,content:{...content,sections:[{...section,transitionText:transitionText||null}]}})}/><TextField label={t('recruitment.fields.question')} multiline value={question.prompt} onChangeText={prompt=>setValue({...value,content:{...content,sections:[{...section,questions:[{...question,prompt}]}]}})}/><TextField label={t('recruitment.fields.competency')} value={question.competency} onChangeText={competency=>setValue({...value,content:{...content,sections:[{...section,questions:[{...question,competency}]}]}})}/><TextField label={t('recruitment.fields.rubric')} multiline value={question.rubric} onChangeText={rubric=>setValue({...value,content:{...content,sections:[{...section,questions:[{...question,rubric}]}]}})}/></Card><FormError message={error}/><Button loading={isLoading} onPress={()=>void submit()}>{t('common.save')}</Button></View>;
}
const styles=StyleSheet.create({content:{gap:spacing.xl,paddingVertical:spacing.xxl},stack:{gap:spacing.lg},error:{color:'#B42318'}});

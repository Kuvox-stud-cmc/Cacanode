import { recruitmentApi } from '@/features/recruitment/api/recruitment-api';
import { springApi } from '@/services/api/api';
import { accessTokenStore } from '@/services/auth/access-token-store';
import { createAppStore } from '@/store';

function json(body:unknown,status=200){return new Response(status===204?null:JSON.stringify(body),{status,headers:status===204?undefined:{'Content-Type':'application/json'}})}

describe('tenant recruitment API',()=>{
  beforeAll(()=>jest.useFakeTimers());
  beforeEach(()=>{jest.restoreAllMocks();accessTokenStore.set('tenant-access')});
  afterEach(()=>{accessTokenStore.set(null);jest.clearAllTimers()});
  afterAll(()=>jest.useRealTimers());

  it('uses authenticated tenant CRUD contracts',async()=>{
    const calls:{method:string;path:string;body?:unknown}[]=[];
    jest.spyOn(globalThis,'fetch').mockImplementation(async input=>{
      const request=input as Request;const path=new URL(request.url).pathname;const body=['GET','DELETE'].includes(request.method)?undefined:await request.clone().json();calls.push({method:request.method,path,body});
      if(request.method==='DELETE')return json(null,204);
      return json({id:'record-1',title:'Engineer',fullName:'Ada',email:'ada@example.com'});
    });
    const store=createAppStore();
    await store.dispatch(recruitmentApi.endpoints.createRecruitmentCandidate.initiate({fullName:'Ada',email:'ada@example.com',phone:null,notes:null})).unwrap();
    await store.dispatch(recruitmentApi.endpoints.createRecruitmentApplication.initiate({jobId:'job-1',candidateId:'candidate-1'})).unwrap();
    await store.dispatch(recruitmentApi.endpoints.deleteRecruitmentCandidate.initiate('candidate-1')).unwrap();
    expect(calls).toContainEqual({method:'POST',path:'/api/v1/recruitment/candidates',body:{fullName:'Ada',email:'ada@example.com',phone:null,notes:null}});
    expect(calls).toContainEqual({method:'POST',path:'/api/v1/recruitment/applications',body:{jobId:'job-1',candidateId:'candidate-1'}});
    expect(calls).toContainEqual({method:'DELETE',path:'/api/v1/recruitment/candidates/candidate-1'});
    const request=(globalThis.fetch as jest.Mock).mock.calls[0][0] as Request;
    expect(request.headers.get('Authorization')).toBe('Bearer tenant-access');
    store.dispatch(springApi.util.resetApiState());
  });

  it('uses exact scheduling, reinvite, completion, and dial endpoints',async()=>{
    const calls:{method:string;path:string;body?:unknown}[]=[];
    jest.spyOn(globalThis,'fetch').mockImplementation(async input=>{const request=input as Request;const path=new URL(request.url).pathname;const body=request.method==='POST'&&request.headers.get('content-type')?.includes('json')?await request.clone().json():undefined;calls.push({method:request.method,path,body});return json({id:'interview-1',status:'SCHEDULED',allowed:true,items:[]})});
    const store=createAppStore();
    await store.dispatch(recruitmentApi.endpoints.sendCompletionLink.initiate('application-1')).unwrap();
    await store.dispatch(recruitmentApi.endpoints.scheduleInterview.initiate({id:'interview-1',startAt:'2026-08-01T02:00:00Z'})).unwrap();
    await store.dispatch(recruitmentApi.endpoints.scheduleInterview.initiate({id:'interview-1',startAt:'2026-08-02T02:00:00Z',reschedule:true})).unwrap();
    await store.dispatch(recruitmentApi.endpoints.reinviteInterview.initiate('interview-1')).unwrap();
    await store.dispatch(recruitmentApi.endpoints.dialInterview.initiate('interview-1')).unwrap();
    expect(calls).toEqual(expect.arrayContaining([
      {method:'POST',path:'/api/v1/recruitment/applications/application-1/completion-link',body:undefined},
      {method:'POST',path:'/api/v1/recruitment/interviews/interview-1/schedule',body:{startAt:'2026-08-01T02:00:00Z'}},
      {method:'POST',path:'/api/v1/recruitment/interviews/interview-1/reschedule',body:{startAt:'2026-08-02T02:00:00Z'}},
      {method:'POST',path:'/api/v1/recruitment/interviews/interview-1/reinvite',body:undefined},
      {method:'POST',path:'/api/v1/recruitment/interviews/interview-1/dial',body:undefined},
    ]));
    store.dispatch(springApi.util.resetApiState());
  });
});

import { emptyJob,emptyTemplate,newScreeningQuestion,normalizeContent,validateJob,validateTemplate } from '@/features/recruitment/model/form-model';

describe('recruitment form rules',()=>{
  it('requires publish-only job configuration without coupling UI locale',()=>{
    const job={...emptyJob(),title:'Engineer',description:'Build products',language:'vi-VN' as const};
    expect(validateJob(job)).toBeNull();
    expect(validateJob(job,true)).toBe('publishRequirements');
    expect(job.language).toBe('vi-VN');
  });

  it('validates screening questions and accepted answers',()=>{
    const question=newScreeningQuestion();
    const job={...emptyJob(),title:'Engineer',description:'Build products',screeningQuestions:[question]};
    expect(validateJob(job)).toBe('screeningInvalid');
    const completed={...question,prompt:'Can you work evenings?',options:question.options.map((option,index)=>({...option,label:index?'No':'Yes'}))};
    expect(validateJob({...job,screeningQuestions:[completed]})).toBeNull();
  });

  it('normalizes immutable template revision ordering',()=>{
    const template=emptyTemplate();
    const section=template.content.sections[0];
    const content=normalizeContent({...template.content,sections:[{...section,position:9,questions:[{...section.questions[0],position:8,prompt:'Tell me about a project',competency:'Delivery',rubric:'Specific evidence'}]}]});
    expect(content.sections[0].position).toBe(1);
    expect(content.sections[0].questions[0].position).toBe(1);
    expect(validateTemplate({...template,name:'Structured interview',content})).toBeNull();
  });
});

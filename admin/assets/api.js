export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const errors = {
 INVALID_CREDENTIALS:'The account details or verification code are incorrect.',
 ADMIN_REQUIRED:'This workspace is available to administrators only.',
 ADMIN_FLOW_EXPIRED:'Your sign-in attempt expired. Start again with your phone number and password.',
 OTP_PROVIDER_UNAVAILABLE:'WhatsApp is temporarily unavailable. Try again or use your recovery password.',
 OTP_RATE_LIMIT:'Wait at least 60 seconds before requesting another code. There is also an hourly limit.',
 OTP_INVALID:'This code is incorrect, expired, or already used. Request a new code.',
 WHATSAPP_OTP_REQUIRED:'Request a WhatsApp code before continuing.',
 TOTP_REQUIRED:'This account uses an authenticator. Enter its current code or use recovery.',
 TOTP_REUSED:'That authenticator code was already used. Wait for the next code.',
 RECOVERY_LOCKED:'Recovery is locked after repeated attempts. Try again in 15 minutes.',
 RECOVERY_PASSWORD_ALREADY_SHOWN:'The replacement recovery password was already shown or has expired.',
 VERIFICATION_ATTEMPTS_EXCEEDED:'Too many verification attempts. Start a new sign-in.',
 RATE_LIMITED:'Too many requests. Wait a minute and try again.',
 ADMIN_SESSION_BUSY:'Your session is being refreshed. Try again in a moment.',
 CANNOT_SUSPEND_SELF:'You cannot suspend your own administrator account.',
 MAP_APPROVAL_REQUIRED:'Routing categories must be approved before this area can be activated.',
 FARES_REQUIRED:'Publish at least one fare plan before activating this area.',
 DOCUMENT_NOT_AVAILABLE:'This document is not available for review until its scan is clean.',
};
export async function api(path, {method='GET',body,key}={}) {
 const headers={'X-Admin-Request':'1'};
 if(body!==undefined)headers['Content-Type']='application/json';
 if(!['GET','HEAD'].includes(method)&&/^\/v1\/admin\//.test(path)) headers['Idempotency-Key']=key??crypto.randomUUID();
 const response=await fetch(path,{method,headers,credentials:'same-origin',cache:'no-store',body:body===undefined?undefined:JSON.stringify(body)});
 let result;try{result=await response.json();}catch{throw new Error('The server returned an unexpected response. Please try again.');}
 if(!response.ok){const detail=result.details?.map(d=>`${d.path?.join('.')??'Field'}: ${d.message}`).join('; ');const error=new Error(errors[result.code]??detail??`Request failed (${result.code??response.status}).`);error.code=result.code;error.status=response.status;throw error;}
 return result;
}
export function notice(message,error=false){const el=$('#notice');if(!el)return;el.textContent=message;el.classList.toggle('error',error);el.hidden=!message;}
export async function busy(button,fn){if(button?.disabled)return;const before=button?.textContent;if(button){button.disabled=true;button.textContent='Please wait…';}try{return await fn();}catch(error){notice(error.message,true);if(['ADMIN_SESSION_EXPIRED','SESSION_REVOKED','INVALID_TOKEN'].includes(error.code)&&!['login','verify','recovery'].includes(document.body.dataset.page))location.assign('/admin/login/?expired=1');return undefined;}finally{if(button){button.disabled=false;button.textContent=before;}}}
export function el(tag,text,classes){const node=document.createElement(tag);if(text!==undefined&&text!==null)node.textContent=String(text);if(classes)node.className=classes;return node;}
export const money = value => new Intl.NumberFormat('en-LK',{style:'currency',currency:'LKR'}).format(Number(value??0)/100);
export const date = value => value ? new Intl.DateTimeFormat('en-GB',{dateStyle:'medium',timeStyle:'short',timeZone:'Asia/Colombo'}).format(new Date(value)) : '—';
export const short = value => value ? String(value).slice(0,8) : '—';
export function badge(value){const text=String(value??'Unknown');const node=el('span',text.replaceAll('_',' ').toLowerCase(),'badge');const color=['APPROVED','AVAILABLE','CLEAN','COMPLETED','PAID','ACTIVE','ONLINE','true'].includes(text)?'green':['PENDING','SEARCHING','SCHEDULED','DRAFT','REQUESTED'].includes(text)?'amber':['REJECTED','SUSPENDED','INFECTED','CANCELLED','DEAD'].includes(text)?'red':['ACCEPTED','ARRIVED','IN_PROGRESS','AT_STOP','RETURNING','EN_ROUTE'].includes(text)?'blue':'slate';node.classList.add('badge-'+color);return node;}
export function action(text,fn,destructive=false){const b=el('button',text,destructive?'btn-danger':'btn-small');b.type='button';b.addEventListener('click',()=>busy(b,fn));return b;}
export function row(values){const tr=el('tr');for(const value of values){const td=el('td');if(value instanceof Node)td.append(value);else td.textContent=value??'—';tr.append(td);}return tr;}
export function empty(tbody,columns,message='No records yet.'){tbody.replaceChildren();const tr=el('tr'),td=el('td',message,'empty');td.colSpan=columns;tr.append(td);tbody.append(tr);}
export function details(data){const dl=el('dl',null,'detail-grid');for(const [key,value] of Object.entries(data)){const group=el('div');group.append(el('dt',key.replaceAll('_',' '),'detail-label'),el('dd',typeof value==='object'?JSON.stringify(value):value??'—','detail-value'));dl.append(group);}return dl;}
export function decimalMinor(value){if(!/^\d+(?:\.\d{1,2})?$/.test(String(value)))throw new Error('Enter a non-negative amount with at most two decimal places.');const [whole,part='']=String(value).split('.');const n=Number(whole)*100+Number(part.padEnd(2,'0'));if(!Number.isSafeInteger(n))throw new Error('Amount is too large.');return n;}

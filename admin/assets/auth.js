import { $,api,notice,busy } from './api.js';
const page=document.body.dataset.page;
if(page==='login'){
 const params=new URLSearchParams(location.search);
 if(!params.has('reauth')){
  const submit=$('#login-form button[type=submit]');submit.disabled=true;
  try{const current=await api('/v1/admin-web/session');location.replace(current.next??'/admin/');}
  catch(error){if(error.status!==401&&error.status!==403)notice('We could not restore your session. Please try again before signing in.',true);}
  finally{submit.disabled=false;}
 }

 if(new URLSearchParams(location.search).has('expired'))notice('Your session ended. Please sign in again.');
 $('#login-form').addEventListener('submit',event=>{event.preventDefault();const form=event.currentTarget;busy($('button[type=submit]',form),async()=>{const data=new FormData(form);const result=await api('/v1/admin-web/begin',{method:'POST',body:{phone:data.get('phone').trim(),password:data.get('password')}});form.reset();location.assign(result.next);});});
}else if(page==='recovery-saved'){
 busy(null,async()=>{const result=await api('/v1/admin-web/recovery-password');$('#new-recovery-password').value=result.recoveryPassword;});
 $('#copy-recovery').addEventListener('click',()=>busy($('#copy-recovery'),async()=>{await navigator.clipboard.writeText($('#new-recovery-password').value);notice('Copied. Save it in your password manager.');}));
 $('#recovery-saved').addEventListener('change',event=>{$('#continue-dashboard').disabled=!event.target.checked;});
 $('#continue-dashboard').addEventListener('click',()=>{$('#new-recovery-password').value='';location.assign('/admin/');});
}else{
 busy(null,async()=>{try{const state=await api('/v1/admin-web/flow');if(page==='verify'){$('#verification-phone').textContent='Account ending in '+state.phone.replace('•••• ','');$('#verification-description').textContent=state.method==='totp'?'Open your authenticator app and enter its current 6-digit RideNow code.':'We’ll send a 6-digit code to your WhatsApp account.';$('#code-label').textContent=state.method==='totp'?'Authenticator app code':'WhatsApp verification code';$('#verification-method').textContent=state.method==='totp'?'AUTHENTICATOR APP':'WHATSAPP';$('#send-otp').hidden=state.method==='totp';if(state.otpRequested)$('#send-otp').textContent='Resend WhatsApp code';if($('#recovery-link'))$('#recovery-link').hidden=!state.recoveryAllowed;}}catch(error){if(error.code==='ADMIN_FLOW_EXPIRED'){location.replace('/admin/login/?expired=1');return;}throw error;}});
 $('#send-otp')?.addEventListener('click',()=>busy($('#send-otp'),async()=>{await api('/v1/admin-web/send-otp',{method:'POST',body:{}});notice('Code requested. Check WhatsApp; the code expires in 5 minutes.');setTimeout(()=>{$('#send-otp').textContent='Resend WhatsApp code';},0);}));
 const form=$('#verify-form')??$('#recovery-form');
 form.addEventListener('submit',event=>{event.preventDefault();busy($('button[type=submit]',form),async()=>{const data=new FormData(form);const body=page==='recovery'?{recoveryPassword:data.get('recoveryPassword').trim()}:{code:data.get('code').trim()};const result=await api('/v1/admin-web/verify',{method:'POST',body});form.reset();location.assign(result.next);});});
}

import { $,api,notice,busy } from './api.js';
const page=document.body.dataset.page;
if(page==='login'){
 const params=new URLSearchParams(location.search);
 const form=$('#login-form'),submit=$('button[type=submit]',form),captcha=$('#recaptcha-token'),captchaControl=$('#captcha-control'),captchaHelp=$('#captcha-help');
 let captchaReady=false,captchaEnabled=false,captchaTestMode=false,widgetId,captchaPoll;
 const media=matchMedia('(prefers-color-scheme:dark)');
 const updateSubmit=()=>{
  if(captchaEnabled&&captchaReady&&typeof window.grecaptcha?.getResponse==='function'){
   try{const response=window.grecaptcha.getResponse(widgetId);if(response)captcha.value=response;}catch{}
  }
  submit.disabled=!captchaReady||(captchaEnabled&&!captcha.value);
 };
 const loadGoogleRecaptcha=()=>new Promise((resolve,reject)=>{
  window.rideNowRecaptchaLoaded=resolve;
  const script=document.createElement('script');script.async=true;script.defer=true;
  script.src='https://www.google.com/recaptcha/api.js?onload=rideNowRecaptchaLoaded&render=explicit';
  script.onerror=()=>reject(new Error('Google reCAPTCHA could not be loaded.'));
  document.head.append(script);
 });
 let siteKey='';
 const renderWidget=()=>{
  captcha.value='';
  const old=$('#recaptcha-widget'),next=document.createElement('div');next.id='recaptcha-widget';old.replaceWith(next);
  widgetId=window.grecaptcha.render(next,{sitekey:siteKey,theme:media.matches?'dark':'light',callback:value=>{captcha.value=value;updateSubmit();},'expired-callback':()=>{captcha.value='';updateSubmit();},'error-callback':()=>{captcha.value='';captchaHelp.textContent='Verification could not complete. Check your connection and try again.';updateSubmit();}});
  captchaReady=true;
  updateSubmit();
 };
 submit.disabled=true;
 if(!params.has('reauth')){
  try{const current=await api('/v1/admin-web/session');location.replace(current.next??'/admin/');}
  catch(error){if(error.status!==401&&error.status!==403)notice('We could not restore your session. Please try again before signing in.',true);}
 }

 if(new URLSearchParams(location.search).has('expired'))notice('Your session ended. Please sign in again.');
 try{
  const captchaConfig=await api('/v1/admin-web/captcha-config');
  captchaEnabled=captchaConfig.enabled;captchaTestMode=captchaConfig.testMode;siteKey=captchaConfig.siteKey;
  if(captchaTestMode){captcha.value='captcha-test-token-value';captchaReady=true;}
  else if(!captchaEnabled)captchaReady=true;
  else{
   captchaControl.hidden=false;
   await loadGoogleRecaptcha();
   renderWidget();
   captchaPoll=window.setInterval(updateSubmit,250);
   media.addEventListener('change',renderWidget);
  }
 }catch(error){captchaHelp.textContent='Human verification is temporarily unavailable. Refresh the page to try again.';notice('Human verification could not be loaded. Please try again shortly.',true);}
 updateSubmit();
 form.addEventListener('submit',async event=>{
  event.preventDefault();
  if(!captchaReady||(captchaEnabled&&!captcha.value)){notice('Complete the human verification before continuing.',true);return;}
  await busy(submit,async()=>{
   const data=new FormData(form);
   try{
    const result=await api('/v1/admin-web/begin',{method:'POST',body:{phone:data.get('phone').trim(),password:data.get('password'),captchaToken:captcha.value||undefined}});
    form.reset();location.assign(result.next);
   }catch(error){
    if(captchaEnabled){captcha.value='';if(captchaTestMode)captcha.value='captcha-test-token-value';else if(window.grecaptcha&&widgetId!==undefined)window.grecaptcha.reset(widgetId);updateSubmit();}
    throw error;
   }
  });
  updateSubmit();
 });
}else if(page==='recovery-saved'){
 busy(null,async()=>{const result=await api('/v1/admin-web/recovery-password');$('#new-recovery-password').value=result.recoveryPassword;});
 $('#copy-recovery').addEventListener('click',()=>busy($('#copy-recovery'),async()=>{await navigator.clipboard.writeText($('#new-recovery-password').value);notice('Copied. Save it in your password manager.');}));
 $('#recovery-saved').addEventListener('change',event=>{$('#continue-dashboard').disabled=!event.target.checked;});
 $('#continue-dashboard').addEventListener('click',()=>{$('#new-recovery-password').value='';location.assign('/admin/');});
}else{
 const codeFields=page==='verify'?[...document.querySelectorAll('[data-code-digit]')]:[];
 const setCode=(start,digits)=>{
  const value=digits.replace(/\D/g,'').slice(0,codeFields.length-start);
  [...value].forEach((digit,offset)=>{codeFields[start+offset].value=digit;});
  for(let index=start+value.length;index<codeFields.length;index++)codeFields[index].value='';
  const focusIndex=Math.min(start+value.length,codeFields.length-1);
  codeFields[focusIndex]?.focus();
 };
 codeFields.forEach((field,index)=>{
  field.addEventListener('input',()=>{
   const value=field.value.replace(/\D/g,'');
   if(value.length>1){setCode(index,value);return;}
   field.value=value.slice(0,1);
   if(field.value&&index<codeFields.length-1)codeFields[index+1].focus();
  });
  field.addEventListener('paste',event=>{
   event.preventDefault();
   const value=event.clipboardData?.getData('text')??'';
   setCode(index,value);
  });
  field.addEventListener('keydown',event=>{
   if(event.key==='Backspace'&&!field.value&&index>0){event.preventDefault();codeFields[index-1].focus();}
   else if(event.key==='ArrowLeft'&&index>0){event.preventDefault();codeFields[index-1].focus();}
   else if(event.key==='ArrowRight'&&index<codeFields.length-1){event.preventDefault();codeFields[index+1].focus();}
  });
 });
 busy(null,async()=>{try{const state=await api('/v1/admin-web/flow');if(page==='verify'){$('#verification-phone').textContent='Account ending in '+state.phone.replace('•••• ','');$('#verification-description').textContent=state.method==='totp'?'Open your authenticator app and enter its current 6-digit RideNow code.':'We’ll send a 6-digit code to your WhatsApp account.';$('#code-label').textContent=state.method==='totp'?'Authenticator app code':'WhatsApp verification code';$('#verification-method').textContent=state.method==='totp'?'AUTHENTICATOR APP':'WHATSAPP';$('#send-otp').hidden=state.method==='totp';if(state.otpRequested)$('#send-otp').textContent='Resend WhatsApp code';if($('#recovery-link'))$('#recovery-link').hidden=!state.recoveryAllowed;}}catch(error){if(error.code==='ADMIN_FLOW_EXPIRED'){location.replace('/admin/login/?expired=1');return;}throw error;}});
 $('#send-otp')?.addEventListener('click',()=>busy($('#send-otp'),async()=>{await api('/v1/admin-web/send-otp',{method:'POST',body:{}});notice('Code requested. Check WhatsApp; the code expires in 5 minutes.');setTimeout(()=>{$('#send-otp').textContent='Resend WhatsApp code';},0);}));
 const form=$('#verify-form')??$('#recovery-form');
 form.addEventListener('submit',event=>{event.preventDefault();busy($('button[type=submit]',form),async()=>{const data=new FormData(form);const body=page==='recovery'?{recoveryPassword:data.get('recoveryPassword').trim()}:{code:codeFields.map(field=>field.value).join('')};const result=await api('/v1/admin-web/verify',{method:'POST',body});form.reset();location.assign(result.next);});});
}

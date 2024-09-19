import{u as E}from"./index-quHqLWFx.js";import{f as S,a6 as C,r as w,af as I,o as M,s as V,t as e,j as A,y as o,v as _,x as f,a2 as a}from"./libs-BEHY-3cN.js";import{w as D,M as L,c as j,at as q,F as H,au as N,L as $,B as O,q as R,S as z,v as U}from"./arcoDesign-wryN9LOx.js";import{I as Z}from"./index-DBpnGMRo.js";const X=S({__name:"index",setup(G){const h=E(),{t}=C(),i=w(!1),s=w({rememberPassword:!0,token:h.authToken}),r=w(),x=async({errors:n,values:m})=>{var l,c,d;const u=n?Object.keys(n):[];if(u.length>0){(l=r.value)==null||l.scrollToField(u[0]);return}const{token:p,rememberPassword:g}=m;if(!i.value){i.value=!0,(c=r.value)==null||c.setFields({token:{status:"validating",message:""}});try{await h.setAuthToken(p,g),L.success({content:t("login.form.login.success"),resetOnHover:!0})}catch(v){(d=r.value)==null||d.setFields({token:{status:"error",message:`${t("login.form.login.failed")}  ${v.message}`}})}finally{i.value=!1}}},{query:y}=I();return M(()=>{var n;y.token&&(s.value.token=y.token,(n=r.value)==null||n.$emit("submit",{values:s.value,errors:void 0},new Event("submit")))}),(n,m)=>{const u=j,p=Z,g=q,l=H,c=N,d=$,v=O,P=R,B=z,F=U,T=D;return A(),V(T,{justify:"center"},{default:e(()=>[o(F,{xs:24,sm:20,md:16,lg:12,xl:8},{default:e(()=>[o(B,{direction:"vertical",fill:""},{default:e(()=>[o(u,{heading:3},{default:e(()=>[_(f(a(t)("login.form.title")),1)]),_:1}),o(P,{ref_key:"loginForm",ref:r,model:s.value,class:"login-form",layout:"vertical",onSubmit:x},{default:e(()=>[o(l,{field:"token",rules:[{required:!0,message:a(t)("login.form.password.errMsg")},{validator:(k,b)=>{/[a-zA-Z0-9-_]+/.test(k)?b():b(a(t)("login.form.password.errMsg"))}}],"validate-trigger":["change","input"],"hide-label":""},{default:e(()=>[o(g,{modelValue:s.value.token,"onUpdate:modelValue":m[0]||(m[0]=k=>s.value.token=k),placeholder:a(t)("login.form.password.placeholder"),"allow-clear":""},{prefix:e(()=>[o(p)]),_:1},8,["modelValue","placeholder"])]),_:1},8,["rules"]),o(l,{field:"rememberPassword",class:"login-form-password-actions"},{default:e(()=>[o(c,{checked:"rememberPassword","model-value":s.value.rememberPassword},{default:e(()=>[_(f(a(t)("login.form.rememberPassword")),1)]),_:1},8,["model-value"]),o(d,{style:{marginLeft:"auto"},href:"https://github.com/PBH-BTN/PeerBanHelper/wiki/%E5%A6%82%E4%BD%95%E9%87%8D%E7%BD%AEToken"},{default:e(()=>[_(f(a(t)("login.form.forgetPassword")),1)]),_:1})]),_:1}),o(v,{type:"primary","html-type":"submit",long:"",loading:i.value},{default:e(()=>[_(f(a(t)("login.form.login")),1)]),_:1},8,["loading"])]),_:1},8,["model"])]),_:1})]),_:1})]),_:1})}}});export{X as default};

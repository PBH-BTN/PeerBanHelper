import{_ as V,l as w,c as U}from"./index-quHqLWFx.js";import{f as q,a6 as B,am as D,j as F,s as M,t as o,y as e,v as p,x as _,a2 as a}from"./libs-BEHY-3cN.js";import{c as C,Z as P,Y as S,p as A,B as E,T as L,S as N,F as R,q as Z}from"./arcoDesign-wryN9LOx.js";import{I as j}from"./index-DBpnGMRo.js";const z=q({__name:"setToken",props:{modelValue:{required:!0},modelModifiers:{}},emits:["update:modelValue"],setup(i){const{t}=B(),n=D(i,"modelValue"),u=async()=>{n.value.token=await m()},m=async()=>crypto.randomUUID?crypto.randomUUID():(await V(()=>import("./uuid-84gC_vFb.js"),[],import.meta.url)).v4();return(d,s)=>{const f=C,g=P,y=S,T=j,h=A,k=w,v=E,I=L,l=N,b=R,x=Z;return F(),M(l,{direction:"vertical"},{default:o(()=>[e(y,{style:{"text-align":"left"}},{default:o(()=>[e(f,null,{default:o(()=>[p(_(a(t)("page.oobe.setToken.title")),1)]),_:1}),e(g,null,{default:o(()=>[p(_(a(t)("page.oobe.setToken.description")),1)]),_:1})]),_:1}),e(x,{model:n.value,style:{"margin-top":"15vh"}},{default:o(()=>[e(b,{label:"Token",required:""},{default:o(()=>[e(l,null,{default:o(()=>[e(h,{modelValue:n.value.token,"onUpdate:modelValue":s[0]||(s[0]=r=>n.value.token=r),style:{width:"27em"},placeholder:"aa-bb-cc-dd-ee-ff","allow-clear":"",rules:[{required:!0,message:a(t)("login.form.password.errMsg")},{validator:(r,c)=>{/[a-zA-Z0-9-_]+/.test(r)?c():c(a(t)("login.form.password.errMsg"))}}],"validate-trigger":"blur"},{prefix:o(()=>[e(T)]),_:1},8,["modelValue","rules"]),e(I,{content:a(t)("page.oobe.setToken.generate")},{default:o(()=>[e(v,{class:"generate-btn",type:"text",shape:"circle",onClick:u},{icon:o(()=>[e(k)]),_:1})]),_:1},8,["content"])]),_:1})]),_:1})]),_:1},8,["model"])]),_:1})}}}),J=U(z,[["__scopeId","data-v-8c8b7f4d"]]);export{J as default};

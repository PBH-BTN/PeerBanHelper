const __vite__mapDeps=(i,m=__vite__mapDeps,d=(m.f||(m.f=["./dummyChart-BQYT3QuX.js","./echarts-cIka2c3l.js","./libs-Cs9lzzqV.js","./index-y-mIu0It.js","./arcoDesign-B08Y0ebX.js","./index-DRoH6C0v.css"])))=>i.map(i=>d[i]);
import{u as j,a as G,g as X,b as J,_ as Ve,c as Re,f as _e}from"./index-y-mIu0It.js";import{a as g,B as N,ah as q,L as re,al as ve,am as ye,F as Y,an as le,X as be,m as we,S as De,q as H,a5 as ie,C as F,ao as ke,T as xe,R as Ie,o as Ae,w as Le,v as Pe}from"./arcoDesign-B08Y0ebX.js";import{u as Q,E as K,i as Z,a as me,b as Se,c as ee,d as he,e as fe,f as Ue}from"./echarts-cIka2c3l.js";import{f as R,a6 as z,r as V,a as te,c as A,w as ae,a7 as oe,j as f,s as w,t as s,y as r,a2 as o,v as _,x as h,q as Ee,o as ze,I as Be,k as E,F as de,P as ce,A as Oe,n as $e,M as Me,N as Fe,p as pe,u as je,l as Ne,a8 as qe}from"./libs-Cs9lzzqV.js";import{I as We}from"./index-Chiu03kB.js";async function Ge(u,e=!1){const t=j();await t.serverAvailable;const n=new URLSearchParams({type:"count",field:u,filter:e?"0.01":"0"}),a=new URL(G(t.endpoint,"api/statistic/analysis/field?"+n.toString()),location.href);return fetch(a,{headers:X()}).then(d=>(t.assertResponseLogin(d),d.json()))}async function Xe(u,e,t){const n=j();await n.serverAvailable;const a=new URL(G(n.endpoint,"api/chart/geoIpInfo"),location.href),d=u.getTime(),l=e.getTime();return a.searchParams.append("startAt",d.toString()),a.searchParams.append("endAt",l.toString()),a.searchParams.append("bannedOnly",String(t)),fetch(a,{headers:X()}).then(b=>(n.assertResponseLogin(b),b.json()))}async function Je(u,e,t){const n=j();await n.serverAvailable;const a=new URLSearchParams({startAt:u.getTime().toString(),endAt:e.getTime().toString(),type:t,field:"banAt"}),d=new URL(G(n.endpoint,"api/statistic/analysis/date?"+a.toString()),location.href);return fetch(d,{headers:X()}).then(l=>(n.assertResponseLogin(l),l.json()))}async function Ye(u,e){const t=j();await t.serverAvailable;const n=new URLSearchParams({startAt:u.getTime().toString(),endAt:e.getTime().toString()}),a=new URL(G(t.endpoint,"api/chart/trend?"+n.toString()),location.href);return fetch(a,{headers:X()}).then(d=>(t.assertResponseLogin(d),d.json()))}async function He(u,e){const t=j();await t.serverAvailable;const n=new URLSearchParams({startAt:u.getTime().toString(),endAt:e.getTime().toString()}),a=new URL(G(t.endpoint,"api/chart/traffic?"+n.toString()),location.href);return fetch(a,{headers:X()}).then(d=>(t.assertResponseLogin(d),d.json()))}const Qe=R({__name:"ispPie",setup(u){const{t:e}=z();Q([Z,me,Se,ee]);const t=J(),n=V(),a=te({field:"isp",enableThreshold:!0,bannedOnly:!0,range:[g().startOf("day").add(-7,"day").toDate(),new Date]}),d=A(()=>({text:e("page.charts.loading"),color:t.isDark?"rgb(60, 126, 255)":"rgb(22, 93, 255)",textColor:t.isDark?"rgba(255, 255, 255, 0.9)":"rgb(29, 33, 41)",maskColor:t.isDark?"rgba(0, 0, 0, 0.4)":"rgba(255, 255, 255, 0.4)"})),l=V({tooltip:{trigger:"item",appendToBody:!0,formatter:'<p style="word-wrap:break-all"><b>{b}</b></p>  {c} ({d}%)'},legend:{orient:"vertical",left:"right",type:"scroll",right:10,top:20,bottom:20,data:[],textStyle:{overflow:"truncate",width:100},tooltip:{show:!0}},backgroundColor:t.isDark?"rgba(0, 0, 0, 0.0)":void 0,series:[{name:"",type:"pie",radius:"55%",center:["50%","60%"],data:[],emphasis:{itemStyle:{shadowBlur:10,shadowOffsetX:0,shadowColor:"rgba(0, 0, 0, 0.5)"}}}]});ae(a,p=>{D(p.range[0],p.range[1],a.bannedOnly)});const{loading:b,run:D,refresh:O}=oe(Xe,{defaultParams:[g().startOf("day").add(-7,"day").toDate(),new Date,a.bannedOnly],onSuccess:p=>{if(p.data){const i=p.data[a.field];let c;if(a.enableThreshold){const v=i.reduce((y,C)=>y+C.value,0)*.01;c=i.filter(y=>y.value>=v)}else c=i;l.value.legend.data=c.map(m=>m.key),l.value.series[0].data=c.map(m=>({name:m.key==="N/A"&&a.field==="province"?e("page.charts.data.province.na"):m.key,value:m.value})),l.value.series[0].name=e("page.charts.options.field."+a.field)}},onError:p=>{n.value=p}});return(p,i)=>{const c=N,m=q,v=re,y=ve,C=ye,x=Y,I=le,L=be,P=we,U=De,B=H,T=ie,$=F;return f(),w($,{hoverable:"",title:o(e)("page.charts.title.geoip")+(a.bannedOnly?o(e)("page.charts.subtitle.bannedOnly"):"")},{extra:s(()=>[r(T,null,{content:s(()=>[r(B,{model:a},{default:s(()=>[r(x,{field:"field",label:o(e)("page.charts.options.field")},{default:s(()=>[r(C,{modelValue:a.field,"onUpdate:modelValue":i[1]||(i[1]=M=>a.field=M),"trigger-props":{autoFitPopupMinWidth:!0}},{default:s(()=>[r(y,{value:"isp"},{default:s(()=>[_(h(o(e)("page.charts.options.field.isp")),1)]),_:1}),r(y,{value:"province"},{default:s(()=>[_(h(o(e)("page.charts.options.field.province")),1)]),_:1}),r(y,{value:"city"},{default:s(()=>[_(h(o(e)("page.charts.options.field.city")),1)]),_:1}),r(y,{value:"region"},{default:s(()=>[_(h(o(e)("page.charts.options.field.region")),1)]),_:1})]),_:1},8,["modelValue"])]),_:1},8,["label"]),r(x,{field:"range",label:o(e)("page.charts.options.days")},{default:s(()=>[r(I,{modelValue:a.range,"onUpdate:modelValue":i[2]||(i[2]=M=>a.range=M),"show-time":"","value-format":"Date",shortcuts:[{label:o(e)("page.charts.options.shortcut.7days"),value:()=>[o(g)().startOf("day").add(-7,"day").toDate(),new Date]},{label:o(e)("page.charts.options.shortcut.14days"),value:()=>[o(g)().startOf("day").add(-14,"day").toDate(),new Date]},{label:o(e)("page.charts.options.shortcut.30days"),value:()=>[o(g)().startOf("day").add(-30,"day").toDate(),new Date]}]},null,8,["modelValue","shortcuts"])]),_:1},8,["label"]),r(x,{field:"enableThreshold"},{default:s(()=>[r(U,null,{default:s(()=>[r(L,{modelValue:a.enableThreshold,"onUpdate:modelValue":i[3]||(i[3]=M=>a.enableThreshold=M)},null,8,["modelValue"]),r(P,null,{default:s(()=>[_(h(o(e)("page.charts.options.thresold")),1)]),_:1})]),_:1})]),_:1}),r(x,{field:"bannedOnly"},{default:s(()=>[r(U,null,{default:s(()=>[r(L,{modelValue:a.bannedOnly,"onUpdate:modelValue":i[4]||(i[4]=M=>a.bannedOnly=M)},null,8,["modelValue"]),r(P,null,{default:s(()=>[_(h(o(e)("page.charts.options.bannedOnly")),1)]),_:1})]),_:1})]),_:1})]),_:1},8,["model"])]),default:s(()=>[r(v,null,{default:s(()=>[_(h(o(e)("page.charts.options.more")),1)]),_:1})]),_:1})]),default:s(()=>[n.value?(f(),w(m,{key:0,status:"500",title:o(e)("page.charts.error.title"),class:"chart chart-error"},{subtitle:s(()=>[_(h(n.value.message),1)]),extra:s(()=>[r(c,{type:"primary",onClick:i[0]||(i[0]=()=>{n.value=void 0,o(O)()})},{default:s(()=>[_(h(o(e)("page.charts.error.refresh")),1)]),_:1})]),_:1},8,["title"])):(f(),w(o(K),{key:1,class:"chart",option:l.value,loading:o(b),autoresize:"","loading-options":d.value,theme:o(t).isDark?"dark":"light","init-options":{renderer:"svg"}},null,8,["option","loading","loading-options","theme"]))]),_:1},8,["title"])}}});var Ke=Object.defineProperty,Ze=(u,e,t)=>e in u?Ke(u,e,{enumerable:!0,configurable:!0,writable:!0,value:t}):u[e]=t,k=(u,e,t)=>Ze(u,typeof e!="symbol"?e+"":e,t);const se=(u,e)=>{const t=u.__vccOpts||u;for(const[n,a]of e)t[n]=a;return t},et={},tt={class:"cell"};function at(u,e){return f(),E("span",tt,h(""))}const ot=se(et,[["render",at],["__scopeId","data-v-511ca080"]]),st=R({__name:"TileView",props:{tile:{}},setup(u){const e=u,{tile:t}=Oe(e),n=A(()=>{var a=["tile"];return a.push("tile"+t.value.value),t.value.mergedInto||a.push("position_"+t.value.row+"_"+t.value.column),t.value.mergedInto&&a.push("merged"),t.value.isNew()&&a.push("new"),t.value.hasMoved()&&(a.push("row_from_"+t.value.fromRow()+"_to_"+t.value.toRow()),a.push("column_from_"+t.value.fromColumn()+"_to_"+t.value.toColumn()),a.push("isMoving")),a.join(" ")});return(a,d)=>(f(),E("span",{class:$e(n.value)},h(o(t).value),3))}}),nt=se(st,[["__scopeId","data-v-fa56f5f8"]]),rt={class:"overlay"},lt={class:"message"},it=R({__name:"GameEndOverlay",props:{board:{},onrestart:{type:Function}},setup(u){const e=u,{board:t}=Oe(e),n=A(()=>t.value.hasWon()||t.value.hasLost()),a=A(()=>t.value.hasWon()?"Good Job!":t.value.hasLost()?"Game Over":""),d=()=>{e.onrestart&&e.onrestart()};return(l,b)=>Me((f(),E("div",rt,[pe("p",lt,h(a.value),1),pe("button",{class:"tryAgain",onClick:d},"Try again")],512)),[[Fe,n.value]])}}),dt=se(it,[["__scopeId","data-v-4b39d1e3"]]),Ce=class Te{constructor(e,t,n){k(this,"value"),k(this,"row"),k(this,"column"),k(this,"oldRow"),k(this,"oldColumn"),k(this,"markForDeletion"),k(this,"mergedInto"),k(this,"id"),this.value=e||0,this.row=t||-1,this.column=n||-1,this.oldRow=-1,this.oldColumn=-1,this.markForDeletion=!1,this.mergedInto=null,this.id=Te.id++}moveTo(e,t){this.oldRow=this.row,this.oldColumn=this.column,this.row=e,this.column=t}isNew(){return this.oldRow===-1&&!this.mergedInto}hasMoved(){return this.fromRow()!==-1&&(this.fromRow()!==this.toRow()||this.fromColumn()!==this.toColumn())||this.mergedInto!==null}fromRow(){return this.mergedInto?this.row:this.oldRow}fromColumn(){return this.mergedInto?this.column:this.oldColumn}toRow(){return this.mergedInto?this.mergedInto.row:this.row}toColumn(){return this.mergedInto?this.mergedInto.column:this.column}};k(Ce,"id",0);let ct=Ce;const W=class S{constructor(){k(this,"tiles"),k(this,"cells"),k(this,"won"),this.tiles=[],this.cells=[];for(let e=0;e<S.size;++e)this.cells[e]=[this.addTile(),this.addTile(),this.addTile(),this.addTile()];this.addRandomTile(),this.setPositions(),this.won=!1}addTile(e){const t=new ct(e);return this.tiles.push(t),t}moveLeft(){let e=!1;for(let t=0;t<S.size;++t){const n=this.cells[t].filter(d=>d.value!=0),a=[];for(let d=0;d<S.size;++d){let l=n.length>0?n.shift():this.addTile();if(n.length>0&&n[0].value===l.value){const b=l;l=this.addTile(l.value),b.mergedInto=l;const D=n.shift();D.mergedInto=l,l.value+=D.value}a[d]=l,this.won=this.won||l.value===2048,e=e||l.value!==this.cells[t][d].value}this.cells[t]=a}return e}setPositions(){this.cells.forEach((e,t)=>{e.forEach((n,a)=>{n.oldRow=n.row,n.oldColumn=n.column,n.row=t,n.column=a,n.markForDeletion=!1})})}addRandomTile(){const e=[];for(let d=0;d<S.size;++d)for(let l=0;l<S.size;++l)this.cells[d][l].value===0&&e.push({r:d,c:l});const t=Math.floor(Math.random()*e.length),n=e[t],a=Math.random()<S.fourProbability?4:2;this.cells[n.r][n.c]=this.addTile(a)}move(e){this.clearOldTiles();for(let n=0;n<e;++n)this.cells=this.rotateLeft(this.cells);const t=this.moveLeft();for(let n=e;n<4;++n)this.cells=this.rotateLeft(this.cells);return t&&this.addRandomTile(),this.setPositions(),this}clearOldTiles(){this.tiles=this.tiles.filter(e=>!e.markForDeletion),this.tiles.forEach(e=>e.markForDeletion=!0)}hasWon(){return this.won}hasLost(){let e=!1;for(let t=0;t<S.size;++t)for(let n=0;n<S.size;++n){e=e||this.cells[t][n].value===0;for(let a=0;a<4;++a){const d=t+S.deltaX[a],l=n+S.deltaY[a];d<0||d>=S.size||l<0||l>=S.size||(e=e||this.cells[t][n].value===this.cells[d][l].value)}}return!e}rotateLeft(e){const t=e.length,n=e[0].length,a=[];for(let d=0;d<t;++d){a.push([]);for(let l=0;l<n;++l)a[d][l]=e[l][n-d-1]}return a}};k(W,"size",4),k(W,"fourProbability",.1),k(W,"deltaX",[-1,0,1,0]),k(W,"deltaY",[0,-1,0,1]);let ge=W;const ut={class:"board",tabIndex:"1"},pt=R({__name:"BoardView",setup(u){const e=V(new ge),t=d=>{if(!e.value.hasWon()&&d.keyCode>=37&&d.keyCode<=40){d.preventDefault();var l=d.keyCode-37;e.value.move(l)}},n=()=>{e.value=new ge};ze(()=>{window.addEventListener("keydown",t)}),Be(()=>{window.removeEventListener("keydown",t)});const a=A(()=>e.value.tiles.filter(d=>d.value!=0));return(d,l)=>(f(),E("div",ut,[(f(!0),E(de,null,ce(e.value.cells,(b,D)=>(f(),E("div",{key:D},[(f(!0),E(de,null,ce(b,(O,p)=>(f(),w(ot,{key:p}))),128))]))),128)),(f(!0),E(de,null,ce(a.value,(b,D)=>(f(),w(nt,{tile:b,key:D},null,8,["tile"]))),128)),r(dt,{board:e.value,onrestart:n},null,8,["board"])]))}}),mt=se(pt,[["__scopeId","data-v-dd29ef2e"]]),ht={name:"vue-2048",components:{BoardView:mt}};function ft(u,e,t,n,a,d){const l=Ee("BoardView");return f(),w(l,{style:{margin:"0 auto"}})}const ne=se(ht,[["render",ft]]),_t=u=>{u.component(ne.name,ne)};ne.install=_t;const gt={class:"chart"},vt=R({__name:"2048",setup(u){const{t:e}=z();return(t,n)=>{const a=ke,d=xe,l=F;return f(),w(l,{hoverable:"",title:"2048"},{extra:s(()=>[r(d,{content:o(e)("page.charts.tooltip.2048")},{default:s(()=>[r(a)]),_:1},8,["content"])]),default:s(()=>[pe("div",gt,[r(o(ne),{style:{margin:"0 auto"}})])]),_:1})}}}),yt=R({__name:"banLine",setup(u){const{t:e}=z(),t=A(()=>({text:e("page.charts.loading"),color:n.isDark?"rgb(60, 126, 255)":"rgb(22, 93, 255)",textColor:n.isDark?"rgba(255, 255, 255, 0.9)":"rgb(29, 33, 41)",maskColor:n.isDark?"rgba(0, 0, 0, 0.4)":"rgba(255, 255, 255, 0.4)"})),n=J(),a=V();Q([Z,he,fe,ee]);const d=i=>{i==="day"?l.range=[g().startOf("day").add(-7,"day").toDate(),new Date]:l.range=[g().startOf("hour").add(-6,"hour").toDate(),new Date]},l=te({timeStep:"day",range:[g().startOf("day").add(-7,"day").toDate(),new Date]}),b=V({xAxis:{type:"time",max:"dataMax"},yAxis:{type:"value"},tooltip:{trigger:"axis"},series:[{data:[],type:"line",name:e("page.charts.line.options.field")}]});ae(l,i=>{O(i.range[0],i.range[1],i.timeStep)});const{loading:D,run:O,refresh:p}=oe(Je,{defaultParams:[g().startOf("day").add(-7,"day").toDate(),new Date,"day"],onSuccess:i=>{if(i.data){const c=new Map;for(let m=g(l.range[0]);m.isBefore(g(l.range[1]));m=m.add(1,l.timeStep))c.set(m.valueOf(),0);i.data.forEach(m=>{c.set(g(m.timestamp).startOf(l.timeStep).valueOf(),m.count)}),b.value.series[0].data=Array.from(c).sort(([m],[v])=>m-v).map(([m,v])=>[new Date(m),v])}},onError:i=>{a.value=i}});return(i,c)=>{const m=re,v=Ie,y=Ae,C=Y,x=le,I=H,L=ie,P=N,U=q,B=F;return f(),w(B,{hoverable:"",title:o(e)("page.charts.title.line")},{extra:s(()=>[r(L,null,{content:s(()=>[r(I,{model:l},{default:s(()=>[r(C,{field:"timeStep",label:o(e)("page.charts.options.steps"),"label-col-flex":"100px"},{default:s(()=>[r(y,{modelValue:l.timeStep,"onUpdate:modelValue":c[0]||(c[0]=T=>l.timeStep=T),onChange:c[1]||(c[1]=T=>d(T))},{default:s(()=>[r(v,{value:"day"},{default:s(()=>[_(h(o(e)("page.charts.options.day")),1)]),_:1}),r(v,{value:"hour"},{default:s(()=>[_(h(o(e)("page.charts.options.hour")),1)]),_:1})]),_:1},8,["modelValue"])]),_:1},8,["label"]),r(C,{field:"range",label:o(e)("page.charts.options.days"),"label-col-flex":"100px"},{default:s(()=>[r(x,{modelValue:l.range,"onUpdate:modelValue":c[2]||(c[2]=T=>l.range=T),"show-time":"","value-format":"Date",shortcuts:l.timeStep==="day"?[{label:o(e)("page.charts.options.shortcut.7days"),value:()=>[o(g)().startOf("day").add(-7,"day").toDate(),new Date]},{label:o(e)("page.charts.options.shortcut.14days"),value:()=>[o(g)().startOf("day").add(-14,"day").toDate(),new Date]},{label:o(e)("page.charts.options.shortcut.30days"),value:()=>[o(g)().startOf("day").add(-30,"day").toDate(),new Date]}]:[{label:o(e)("page.charts.options.shortcut.6hours"),value:()=>[o(g)().startOf("hour").add(-6,"hour").toDate(),new Date]},{label:o(e)("page.charts.options.shortcut.12hours"),value:()=>[o(g)().startOf("hour").add(-12,"hour").toDate(),new Date]},{label:o(e)("page.charts.options.shortcut.24hours"),value:()=>[o(g)().startOf("hour").add(-24,"hour").toDate(),new Date]}]},null,8,["modelValue","shortcuts"])]),_:1},8,["label"])]),_:1},8,["model"])]),default:s(()=>[r(m,null,{default:s(()=>[_(h(o(e)("page.charts.options.more")),1)]),_:1})]),_:1})]),default:s(()=>[a.value?(f(),w(U,{key:0,status:"500",title:o(e)("page.charts.error.title"),class:"chart chart-error"},{subtitle:s(()=>[_(h(a.value.message),1)]),extra:s(()=>[r(P,{type:"primary",onClick:c[3]||(c[3]=()=>{a.value=void 0,o(p)()})},{default:s(()=>[_(h(o(e)("page.charts.error.refresh")),1)]),_:1})]),_:1},8,["title"])):(f(),w(o(K),{key:1,class:"chart",option:b.value,loading:o(D),"loading-options":t.value,theme:"ovilia-green",autoresize:"","init-options":{renderer:"svg"}},null,8,["option","loading","loading-options"]))]),_:1},8,["title"])}}}),bt=R({__name:"fieldPie",setup(u){const{t:e}=z();Q([Z,me,Se,ee]);const t=J(),n=te({field:"peerId",enableThreshold:!0,mergeSameVersion:!1}),a=A(()=>({text:e("page.charts.loading"),color:t.isDark?"rgb(60, 126, 255)":"rgb(22, 93, 255)",textColor:t.isDark?"rgba(255, 255, 255, 0.9)":"rgb(29, 33, 41)",maskColor:t.isDark?"rgba(0, 0, 0, 0.4)":"rgba(255, 255, 255, 0.4)"})),d=V(),l=V({tooltip:{trigger:"item",appendToBody:!0,formatter:'<p style="word-wrap:break-all"><b>{b}</b></p>  {c} ({d}%)'},legend:{orient:"vertical",left:"right",type:"scroll",right:10,top:20,bottom:20,data:[],textStyle:{overflow:"truncate",width:100},tooltip:{show:!0}},backgroundColor:t.isDark?"rgba(0, 0, 0, 0.0)":void 0,series:[{name:e("page.charts.options.field."+n.field),type:"pie",radius:"55%",center:["50%","60%"],data:[],emphasis:{itemStyle:{shadowBlur:10,shadowOffsetX:0,shadowColor:"rgba(0, 0, 0, 0.5)"}}}]});ae(n,p=>{D(p.field,p.enableThreshold)});const{loading:b,run:D,refresh:O}=oe(Ge,{defaultParams:["peerId",!0],onSuccess:p=>{if(p.data){const i=p.data.map(c=>(c.data===""&&(c.data=e("page.charts.options.field.empty")),c));if(n.mergeSameVersion&&n.field==="peerId"){const c=new Map;i.forEach(m=>{let v=m.data;const y=v.match(/^([-]?[a-zA-z]+)[0-9]+.*/);y&&(y==null?void 0:y.length)>=2&&(v=y[1]+"*"),c.has(v)?c.set(v,c.get(v)+m.count):c.set(v,m.count)}),l.value.legend.data=[],l.value.series[0].data=[],Array.from(c).forEach(([m,v])=>{l.value.legend.data.push(m),l.value.series[0].data.push({name:m,value:v})})}else l.value.legend.data=i.map(c=>c.data),l.value.series[0].data=i.map(c=>({name:c.data,value:c.count}))}},onError:p=>{d.value=p}});return(p,i)=>{const c=N,m=q,v=re,y=ve,C=ye,x=Y,I=be,L=we,P=De,U=H,B=ie,T=F;return f(),w(T,{hoverable:"",title:o(e)("page.charts.options.field."+n.field)},{extra:s(()=>[r(B,null,{content:s(()=>[r(U,{model:n,style:{width:"25vh"}},{default:s(()=>[r(x,{field:"field",label:o(e)("page.charts.options.field")},{default:s(()=>[r(C,{modelValue:n.field,"onUpdate:modelValue":i[1]||(i[1]=$=>n.field=$),"trigger-props":{autoFitPopupMinWidth:!0}},{default:s(()=>[r(y,{value:"peerId"},{default:s(()=>[_(h(o(e)("page.charts.options.field.peerId")),1)]),_:1}),r(y,{value:"torrentName"},{default:s(()=>[_(h(o(e)("page.charts.options.field.torrentName")),1)]),_:1}),r(y,{value:"module"},{default:s(()=>[_(h(o(e)("page.charts.options.field.module")),1)]),_:1})]),_:1},8,["modelValue"])]),_:1},8,["label"]),r(x,{field:"enableThreshold"},{default:s(()=>[r(P,null,{default:s(()=>[r(I,{modelValue:n.enableThreshold,"onUpdate:modelValue":i[2]||(i[2]=$=>n.enableThreshold=$)},null,8,["modelValue"]),r(L,null,{default:s(()=>[_(h(o(e)("page.charts.options.thresold")),1)]),_:1})]),_:1})]),_:1}),n.field==="peerId"?(f(),w(x,{key:0,field:"mergeSameVersion"},{default:s(()=>[r(P,null,{default:s(()=>[r(I,{modelValue:n.mergeSameVersion,"onUpdate:modelValue":i[3]||(i[3]=$=>n.mergeSameVersion=$)},null,8,["modelValue"]),r(L,null,{default:s(()=>[_(h(o(e)("page.charts.options.mergeSame")),1)]),_:1})]),_:1})]),_:1})):je("",!0)]),_:1},8,["model"])]),default:s(()=>[r(v,null,{default:s(()=>[_(h(o(e)("page.charts.options.more")),1)]),_:1})]),_:1})]),default:s(()=>[d.value?(f(),w(m,{key:0,status:"500",title:o(e)("page.charts.error.title"),class:"chart chart-error"},{subtitle:s(()=>[_(h(d.value.message),1)]),extra:s(()=>[r(c,{type:"primary",onClick:i[0]||(i[0]=()=>{d.value=void 0,o(O)()})},{default:s(()=>[_(h(o(e)("page.charts.error.refresh")),1)]),_:1})]),_:1},8,["title"])):(f(),w(o(K),{key:1,class:"chart",option:l.value,loading:o(b),autoresize:"","loading-options":a.value,theme:o(t).isDark?"dark":"light","init-options":{renderer:"svg"}},null,8,["option","loading","loading-options","theme"]))]),_:1},8,["title"])}}}),wt=R({__name:"plusWarpper",props:{title:{}},setup(u){const e=qe(()=>Ve(()=>import("./dummyChart-BQYT3QuX.js"),__vite__mapDeps([0,1,2,3,4,5]),import.meta.url)),{t}=z(),n=j(),a=A(()=>n.plusStatus),d=()=>{n.emmitter.emit("open-plus-modal")};return(l,b)=>{var c;const D=We,O=N,p=q,i=F;return(c=a.value)!=null&&c.activated?Ne(l.$slots,"default",{key:0},void 0,!0):(f(),w(i,{key:1,hoverable:"",title:l.title},{default:s(()=>[r(p,{class:"overlay",status:"warning",title:o(t)("page.charts.locked")},{icon:s(()=>[r(D)]),subtitle:s(()=>[_(h(o(t)("page.charts.locked.tips")),1)]),extra:s(()=>[r(O,{type:"primary",onClick:d},{default:s(()=>[_(h(o(t)("page.charts.locked.active")),1)]),_:1})]),_:1},8,["title"]),r(o(e))]),_:1},8,["title"]))}}}),ue=Re(wt,[["__scopeId","data-v-fa9c84e8"]]),Dt=R({__name:"traffic",setup(u){Q([Z,me,Ue,fe,he,ee]);const e=te({range:[g().startOf("day").add(-7,"day").toDate(),new Date]}),t=J(),n=A(()=>({text:a("page.charts.loading"),color:t.isDark?"rgb(60, 126, 255)":"rgb(22, 93, 255)",textColor:t.isDark?"rgba(255, 255, 255, 0.9)":"rgb(29, 33, 41)",maskColor:t.isDark?"rgba(0, 0, 0, 0.4)":"rgba(255, 255, 255, 0.4)"})),{t:a,d}=z(),l=V(),b=V({tooltip:{trigger:"axis",axisPointer:{type:"shadow"},formatter:function(i){return d(i[0].data[0],"short")+":<br/>"+i.map(c=>`${c.marker} ${c.seriesName}: ${_e(c.data[1])}`).join("<br>")}},legend:{data:[a("page.charts.traffic.options.download"),a("page.charts.traffic.options.upload")]},xAxis:{type:"time",max:"dataMax",min:"dataMin",minInterval:3600*24*1e3},yAxis:{type:"value",axisLabel:{formatter:i=>_e(i)}},series:[{name:a("page.charts.traffic.options.download"),type:"line",emphasis:{focus:"series"},data:[]},{name:a("page.charts.traffic.options.upload"),type:"line",emphasis:{focus:"series"},data:[]}]});ae(e,i=>{O(i.range[0],i.range[1])});const{loading:D,run:O,refresh:p}=oe(He,{defaultParams:[g().startOf("day").add(-7,"day").toDate(),new Date],onSuccess:i=>{i.data&&(b.value.series[0].data=i.data.map(c=>[new Date(c.timestamp),c.dataOverallDownloaded]),b.value.series[1].data=i.data.map(c=>[new Date(c.timestamp),c.dataOverallUploaded]))},onError:i=>{l.value=i}});return(i,c)=>{const m=re,v=le,y=Y,C=H,x=ke,I=xe,L=ie,P=N,U=q,B=F;return f(),w(B,{hoverable:"",title:o(a)("page.charts.title.traffic")},{extra:s(()=>[r(L,null,{content:s(()=>[r(C,{model:e},{default:s(()=>[r(y,{field:"range",label:o(a)("page.charts.options.days"),"label-col-flex":"100px"},{default:s(()=>[r(v,{modelValue:e.range,"onUpdate:modelValue":c[0]||(c[0]=T=>e.range=T),"show-time":"","value-format":"Date",shortcuts:[{label:o(a)("page.charts.options.shortcut.7days"),value:()=>[o(g)().startOf("day").add(-7,"day").toDate(),new Date]},{label:o(a)("page.charts.options.shortcut.14days"),value:()=>[o(g)().startOf("day").add(-14,"day").toDate(),new Date]},{label:o(a)("page.charts.options.shortcut.30days"),value:()=>[o(g)().startOf("day").add(-30,"day").toDate(),new Date]}]},null,8,["modelValue","shortcuts"])]),_:1},8,["label"])]),_:1},8,["model"])]),default:s(()=>[r(m,null,{default:s(()=>[_(h(o(a)("page.charts.options.more")),1)]),_:1}),r(I,{content:o(a)("page.charts.tooltip.traffic")},{default:s(()=>[r(x)]),_:1},8,["content"])]),_:1})]),default:s(()=>[l.value?(f(),w(U,{key:0,status:"500",title:o(a)("page.charts.error.title"),class:"chart chart-error"},{subtitle:s(()=>[_(h(l.value.message),1)]),extra:s(()=>[r(P,{type:"primary",onClick:c[1]||(c[1]=()=>{l.value=void 0,o(p)()})},{default:s(()=>[_(h(o(a)("page.charts.error.refresh")),1)]),_:1})]),_:1},8,["title"])):(f(),w(o(K),{key:1,class:"chart",option:b.value,loading:o(D),"loading-options":n.value,theme:"ovilia-green",autoresize:"","init-options":{renderer:"svg"}},null,8,["option","loading","loading-options"]))]),_:1},8,["title"])}}}),kt=R({__name:"trends",setup(u){Q([fe,Z,he,ee]);const{t:e}=z(),t=te({range:[g().startOf("day").add(-7,"day").toDate(),new Date]}),n=V(),a=J(),d=A(()=>({text:e("page.charts.loading"),color:a.isDark?"rgb(60, 126, 255)":"rgb(22, 93, 255)",textColor:a.isDark?"rgba(255, 255, 255, 0.9)":"rgb(29, 33, 41)",maskColor:a.isDark?"rgba(0, 0, 0, 0.4)":"rgba(255, 255, 255, 0.4)"})),l=V({xAxis:{type:"time",max:"dataMax"},yAxis:{type:"value"},tooltip:{trigger:"axis"},series:[{data:[],type:"line",color:"#A5A051",areaStyle:{color:"#A5A051"},name:e("page.charts.trends.options.peers")},{data:[],type:"line",color:"#DB4D6D",areaStyle:{color:"#DB4D6D"},name:e("page.charts.trends.options.bans")}]});ae(t,p=>{D(p.range[0],p.range[1])});const{loading:b,run:D,refresh:O}=oe(Ye,{defaultParams:[g().startOf("day").add(-7,"day").toDate(),new Date],onSuccess:p=>{p.data&&(l.value.series[0].data=p.data.connectedPeersTrend.sort((i,c)=>i.key-c.key).map(i=>[new Date(i.key),i.value]),l.value.series[1].data=p.data.bannedPeersTrend.sort((i,c)=>i.key-c.key).map(i=>[new Date(i.key),i.value]))},onError:p=>{n.value=p}});return(p,i)=>{const c=le,m=Y,v=H,y=N,C=q,x=F;return f(),w(x,{hoverable:"",title:o(e)("page.charts.title.trends")},{extra:s(()=>[r(v,{model:t},{default:s(()=>[r(m,{field:"range",label:o(e)("page.charts.options.days"),style:{"margin-bottom":"0"}},{default:s(()=>[r(c,{modelValue:t.range,"onUpdate:modelValue":i[0]||(i[0]=I=>t.range=I),"value-format":"Date",style:{width:"275px"},shortcuts:[{label:o(e)("page.charts.options.shortcut.7days"),value:()=>[o(g)().startOf("day").add(-7,"day").toDate(),new Date]},{label:o(e)("page.charts.options.shortcut.14days"),value:()=>[o(g)().startOf("day").add(-14,"day").toDate(),new Date]},{label:o(e)("page.charts.options.shortcut.30days"),value:()=>[o(g)().startOf("day").add(-30,"day").toDate(),new Date]}]},null,8,["modelValue","shortcuts"])]),_:1},8,["label"])]),_:1},8,["model"])]),default:s(()=>[n.value?(f(),w(C,{key:0,status:"500",title:o(e)("page.charts.error.title"),class:"chart chart-error"},{subtitle:s(()=>[_(h(n.value.message),1)]),extra:s(()=>[r(y,{type:"primary",onClick:i[1]||(i[1]=()=>{n.value=void 0,o(O)()})},{default:s(()=>[_(h(o(e)("page.charts.error.refresh")),1)]),_:1})]),_:1},8,["title"])):(f(),w(o(K),{key:1,class:"chart",option:l.value,loading:o(b),"loading-options":d.value,theme:"ovilia-green",autoresize:"","init-options":{renderer:"svg"}},null,8,["option","loading","loading-options"]))]),_:1},8,["title"])}}}),Vt=R({__name:"index",setup(u){const{t:e}=z();return(t,n)=>{const a=Pe,d=Le;return f(),w(d,{justify:"center",align:"stretch",wrap:!0,gutter:[{xs:12,sm:12,md:12,lg:12,xl:24},{xs:12,sm:12,md:12,lg:12,xl:24}]},{default:s(()=>[r(a,{xl:12,lg:24,md:24,sm:24,xs:24},{default:s(()=>[r(yt)]),_:1}),r(a,{xl:12,lg:24,md:24,sm:24,xs:24},{default:s(()=>[r(bt)]),_:1}),r(a,{xl:12,lg:24,md:24,sm:24,xs:24},{default:s(()=>[r(ue,{title:o(e)("page.charts.title.geoip")},{default:s(()=>[r(Qe)]),_:1},8,["title"])]),_:1}),r(a,{xl:12,lg:24,md:24,sm:24,xs:24},{default:s(()=>[r(vt)]),_:1}),r(a,{xl:24,lg:24,md:24,sm:24,xs:24},{default:s(()=>[r(ue,{title:o(e)("page.charts.title.trends")},{default:s(()=>[r(kt)]),_:1},8,["title"])]),_:1}),r(a,{xl:24,lg:24,md:24,sm:24,xs:24},{default:s(()=>[r(ue,{title:o(e)("page.charts.title.traffic")},{default:s(()=>[r(Dt)]),_:1},8,["title"])]),_:1})]),_:1})}}});export{Vt as default};

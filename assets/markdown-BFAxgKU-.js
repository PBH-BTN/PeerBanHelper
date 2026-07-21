import{F as e,T as t,X as n,k as r,wt as i}from"./echarts-m8SUGX3S.js";import{w as a}from"./vendor-B27TKMuf.js";import{t as o}from"./dark-B7zn2OaX.js";var s=new a({linkify:!0}),c=s.renderer.rules.link_open||function(e,t,n,r,i){return i.renderToken(e,t,n)};s.renderer.rules.link_open=function(e,t,n,r,i){return e[t].attrSet(`target`,`_blank`),c(e,t,n,r,i)};var l=[`srcdoc`],u=[`innerHTML`],d=e({__name:`markdown`,props:{content:{},useGithubMarkdown:{type:Boolean,default:!1}},setup(e){let a=o(),c=t(()=>`
    <html data-theme="${a.isDark?`dark`:`light`}">
    <link rel="stylesheet" href="./style/github-markdown-css.css"/>
    <div class="markdown-body" data-theme="${a.isDark?`dark`:`light`}">${s.render(e.content)}</div>
    </html>
    `);return(t,a)=>e.useGithubMarkdown?(n(),r(`iframe`,{key:0,title:`changelog`,style:{border:`none`,width:`100%`,height:`100%`},srcdoc:c.value,sandbox:``},null,8,l)):(n(),r(`div`,{key:1,innerHTML:i(s).render(e.content)},null,8,u))}});export{d as t};
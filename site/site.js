// morphex browser sketch: tabs + three-rule demo
const SN={
 "Maven":'<dependency>\n  <groupId>io.github.slayyyyer.morphex</groupId>\n  <artifactId>english-inflector-core</artifactId>\n  <version>0.1.0</version>\n</dependency>',
 "Gradle (Kotlin)":'implementation("io.github.slayyyyer.morphex:english-inflector-core:0.1.0")',
 "Gradle (Groovy)":"implementation 'io.github.slayyyyer.morphex:english-inflector-core:0.1.0'",
 "From source":'./mvnw install   # JDK 17+; wrapper pins Maven 3.9.9'
};
const tabs=document.getElementById('tabs'),snip=document.getElementById('snip');
function pick(k){if(!snip||!tabs)return;snip.textContent=SN[k];[...tabs.children].forEach(b=>b.setAttribute('aria-selected',b.textContent===k))}
if(tabs){
Object.keys(SN).forEach(k=>{const b=document.createElement('button');b.className='tab';b.setAttribute('role','tab');b.textContent=k;b.onclick=()=>pick(k);tabs.appendChild(b)});
pick("Maven");
}

const IRR={child:"children",man:"men",woman:"women",foot:"feet",tooth:"teeth",mouse:"mice",louse:"lice",goose:"geese",ox:"oxen",person:"people",die:"dice",sheep:"sheep",fish:"fish",datum:"data",cactus:"cacti",criterion:"criteria",German:"Germans"};
function restore(src,out){return src.length>1&&src===src.toUpperCase()&&/[A-Z]/.test(src)&&!/^[A-Z]{2,4}$/.test(src.replace(/[^A-Za-z]/g,''))?out.toUpperCase():out}
function plural(w){
 const s=w.trim();if(!s)return["—",""];
 const low=s.toLowerCase();
 if(Object.prototype.hasOwnProperty.call(IRR,low))return[restore(s,IRR[low]),"lexicon entry · lex:"+low];
 if(/^(.+)-in-law$/i.test(s)){const h=plural(s.replace(/-in-law$/i,''))[0];return[h+s.slice(s.length-7),"supported compound · postposed head"]}
 if(/^[A-Z]{2,5}$/.test(s)&&!(s in IRR)){return[s+"s","productive rule · acronym +s"]}
 if(/(s|x|z|ch|sh)$/i.test(s))return[s+"es","productive rule · +es after sibilant"];
 if(/[^aeiou]y$/i.test(s))return[s.slice(0,-1)+"ies","productive rule · consonant-y → ies"];
 return[s+"s","productive rule · default +s"];
}
const w=document.getElementById('w'),o=document.getElementById('o'),y=document.getElementById('y');
function run(){if(!w||!o)return;const r=plural(w.value);o.textContent=r[0];if(y)y.textContent=r[1]?"→ "+r[1]:""}
if(w){w.addEventListener('input',run);}
["child","CHILD","blouse","UFO","mother-in-law","city","box","die"].forEach(t=>{const c=document.getElementById('chips');if(!c)return;const b=document.createElement('button');b.className='chip';b.textContent=t;b.onclick=()=>{w.value=t;run()};c.appendChild(b)});
run();

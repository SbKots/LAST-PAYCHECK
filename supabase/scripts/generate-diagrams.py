"""ER export in the original practice 8 notation; no database connection."""
from pathlib import Path
import hashlib,heapq,html,json,re
from xml.etree.ElementTree import Element,SubElement,ElementTree
ROOT=next(p for p in Path(__file__).resolve().parents if (p/'supabase/migrations').is_dir())
OUT=ROOT/'docs/diagrams'
for name,digest in json.loads((ROOT/'supabase/contracts/migration-checksums.json').read_text()).items():
 assert hashlib.sha256((ROOT/'supabase/migrations'/name).read_bytes()).hexdigest()==digest
tables={};fks=[]
for block in (ROOT/'docs/supabase/data-dictionary.md').read_text(encoding='utf-8').split('## ')[1:]:
 name=block.splitlines()[0].strip()
 if not name.startswith(('lastpay.','lastpay_private.')):continue
 cols=[dict(name=n,type=t.strip().replace('character varying','varchar').replace('character(','char(').replace('timestamp with time zone','timestamptz'),required=r=='True') for n,t,r in re.findall(r'^\| (\w+) \| ([^|]+) \| (True|False) \|',block,re.M)]
 pk=re.search(r'PRIMARY KEY \(([^)]+)\)',block)
 tables[name]=dict(cols=cols,pk={x.strip() for x in pk.group(1).split(',')} if pk else set())
 for child,parent,keys in re.findall(r'FOREIGN KEY \(([^)]+)\) REFERENCES ([\w.]+)\(([^)]+)\)',block):
  fks.append(dict(child=name,parent=parent,childKeys=[x.strip() for x in child.split(',')],parentKeys=[x.strip() for x in keys.split(',')]))
assert len(tables)==18 and len(fks)==22
tables['auth.users']=dict(cols=[dict(name='id',type='uuid',required=True)],pk={'id'})
for name,t in tables.items():t['fk']={k for f in fks if f['child']==name for k in f['childKeys']}
for f in fks:
 assert set(f['childKeys'])<={c['name'] for c in tables[f['child']]['cols']}
 assert set(f['parentKeys'])<={c['name'] for c in tables[f['parent']]['cols']}
order=[['lastpay.occupation','lastpay.skill','auth.users','lastpay.car_brand','lastpay.car_model'],['lastpay.occupation_skill','lastpay.job_definition','lastpay.user_save','lastpay.car_specification','lastpay.car_balance'],['lastpay.inflation_observation','lastpay.provider_cache','lastpay.catalog_version','lastpay.race_track','lastpay.race_configuration'],['lastpay_private.provider_lease','lastpay_private.rate_window','lastpay_private.admin_membership','lastpay_private.iso_country']]
main={n:(20+i*330,85+j*230) for j,row in enumerate(order) for i,n in enumerate(row)}
groups=[['lastpay.occupation','lastpay.skill','lastpay.occupation_skill','lastpay.job_definition','lastpay.inflation_observation','lastpay.provider_cache'],['auth.users','lastpay.user_save','lastpay_private.admin_membership','lastpay.catalog_version','lastpay_private.provider_lease','lastpay_private.rate_window','lastpay_private.iso_country'],['lastpay.car_brand','lastpay.car_model','lastpay.car_specification','lastpay.car_balance','lastpay.race_track','lastpay.race_configuration']]
details={};ys=[100]*3
for i,group in enumerate(groups):
 for name in group:details[name]=(60+750*i,ys[i]);ys[i]+=34+28*len(tables[name]['cols'])+115
mx=Element('mxfile',host='app.diagrams.net',compressed='false',pages='2')
def build(detail=False):
 width,height=(2140,max(ys)+20) if detail else (1680,1030)
 w,head,rowh=(510,34,28) if detail else (300,48,28)
 pos=details if detail else main
 cols={n:[c for c in t['cols'] if detail or c['name'] in (t['pk']|t['fk'])] for n,t in tables.items()}
 nodes={n:(x,y,w,head+len(cols[n])*rowh) for n,(x,y) in pos.items()}
 diag=SubElement(mx,'diagram',id='detail' if detail else 'main',name='Все поля' if detail else 'Общая ER модель')
 model=SubElement(diag,'mxGraphModel',page='1',pageWidth=str(width),pageHeight=str(height),grid='1',gridSize='10',background='#FFFFFF')
 root=SubElement(model,'root');SubElement(root,'mxCell',id='0');SubElement(root,'mxCell',id='1',parent='0')
 prefix='d-' if detail else 'm-'
 nid=lambda n:prefix+n.replace('.','_')
 svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">','<rect width="100%" height="100%" fill="white"/>']
 def line(points,dash=False):
  path=' '.join(('M' if i==0 else 'L')+f'{x},{y}' for i,(x,y) in enumerate(points))
  svg.append(f'<path d="{path}" fill="none" stroke="black" stroke-width="1.2"'+(' stroke-dasharray="5 3"' if dash else '')+'/>')
 def text(x,y,t,size=20,bold=False,center=False,gray=False,under=False):
  svg.append(f'<text x="{x}" y="{y}" font-family="Arial" font-size="{size}" font-weight="'+('bold' if bold else 'normal')+'" text-anchor="'+('middle' if center else 'start')+'" fill="'+('#555555' if gray else 'black')+'"'+(' text-decoration="underline"' if under else '')+'>'+html.escape(t)+'</text>')
 blocked=set();used=set();step=10
 for x,y,nw,nh in nodes.values():
  blocked.update((a,b) for a in range(int((x-4)//10),int((x+nw+4)//10)+1) for b in range(int((y-4)//10),int((y+nh+4)//10)+1))
 def route(a,b):
  start=(round(a[0]/10),round(a[1]/10));goal=(round(b[0]/10),round(b[1]/10));queue=[(0,0,start)];cost={start:0};prev={}
  while queue:
   _,g,p=heapq.heappop(queue)
   if p==goal:break
   if g!=cost[p]:continue
   for q in [(p[0]+1,p[1]),(p[0]-1,p[1]),(p[0],p[1]+1),(p[0],p[1]-1)]:
    if q not in (start,goal) and (q in blocked or not 0<=q[0]<width//10-1 or not 6<=q[1]<height//10-5):continue
    ng=g+1+(3 if q in used else 0)
    if ng<cost.get(q,10**9):cost[q]=ng;prev[q]=p;heapq.heappush(queue,(ng+abs(q[0]-goal[0])+abs(q[1]-goal[1]),ng,q))
  else:raise RuntimeError(('No route',a,b))
  path=[goal]
  while path[-1]!=start:path.append(prev[path[-1]])
  path.reverse();used.update(path);path=[a]+[(x*10,y*10) for x,y in path]+[b];out=[path[0]]
  for p,q in zip(path[1:],path[2:]+[path[-1]]):
   if out[-1][0]==p[0]==q[0] or out[-1][1]==p[1]==q[1]:continue
   out.append(p)
  if out[-1]!=b:out.append(b)
  return out
 def port(n,field,right):
  x,y,nw,nh=nodes[n];i=next(i for i,c in enumerate(cols[n]) if c['name']==field)
  return (x+nw if right else x,y+head+i*rowh+rowh/2)
 def mark(p,out,many=False,optional=False):
  x,y=p;dx=1 if out[0]>x else -1
  if many:line([(x,y-6),(x+dx*11,y),(x,y+6)])
  else:line([(x+dx*6,y-6),(x+dx*6,y+6)])
  if optional:svg.append(f'<circle cx="{x+dx*17}" cy="{y}" r="4" fill="white" stroke="black"/>')
  else:line([(x+dx*13,y-6),(x+dx*13,y+6)])
 for i,f in enumerate(fks):
  parent,child=f['parent'],f['child'];right=nodes[child][0]>nodes[parent][0] if nodes[child][0]!=nodes[parent][0] else i%2==0
  pk,ck=f['parentKeys'][-1],f['childKeys'][-1];a=port(parent,pk,right);b=port(child,ck,not right)
  aa=(a[0]+(20 if right else -20),a[1]);bb=(b[0]+(-20 if right else 20),b[1]);points=[a]+route(aa,bb)+[b]
  nullable=any(not next(c['required'] for c in tables[child]['cols'] if c['name']==k) for k in f['childKeys'])
  single=set(f['childKeys'])==tables[child]['pk'];dash=parent=='lastpay.catalog_version'
  line(points,dash);mark(a,aa,optional=nullable);mark(b,bb,many=not single,optional=True)
  style='edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;strokeColor=#000000;strokeWidth=1.1;startArrow='+('ERzeroToOne' if nullable else 'ERmandOne')+';endArrow='+('ERzeroToOne' if single else 'ERzeroToMany')+';startFill=0;endFill=0;'+('dashed=1;' if dash else '')
  edge=SubElement(root,'mxCell',id=prefix+'fk'+str(i),edge='1',parent='1',source=nid(parent)+'-'+pk,target=nid(child)+'-'+ck,style=style,tooltip=child+'('+','.join(f['childKeys'])+') → '+parent+'('+','.join(f['parentKeys'])+')')
  geom=SubElement(edge,'mxGeometry',relative='1',**{'as':'geometry'});arr=SubElement(geom,'Array',**{'as':'points'})
  for x,y in points[1:-1]:SubElement(arr,'mxPoint',x=str(x),y=str(y))
 for name,(x,y,nw,nh) in nodes.items():
  t=tables[name];schema,short=name.split('.');ident=nid(name)
  cell=SubElement(root,'mxCell',id=ident,value=name if detail else schema+'<br>'+short,vertex='1',parent='1',style=f'shape=table;startSize={head};container=1;collapsible=1;childLayout=tableLayout;fixedRows=1;rowLines=0;fontStyle=1;align=center;html=1;fontFamily=Arial;fontSize={16 if detail else 22};fillColor=#FFFFFF;strokeColor=#000000;strokeWidth=1.2;')
  SubElement(cell,'mxGeometry',x=str(x),y=str(y),width=str(nw),height=str(nh),**{'as':'geometry'})
  svg.append(f'<rect x="{x}" y="{y}" width="{nw}" height="{nh}" fill="white" stroke="black" stroke-width="1.2"/>')
  if detail:text(x+nw/2,y+23,name,16,True,True)
  else:text(x+nw/2,y+17,schema,14,False,True);text(x+nw/2,y+39,short,22,True,True)
  line([(x,y+head),(x+nw,y+head)])
  widths=[64,294,152] if detail else [52,162,86]
  for i,c in enumerate(cols[name]):
   field=c['name'];pk=field in t['pk'];fk=field in t['fk'];tag='PK, FK' if pk and fk else 'PK' if pk else 'FK' if fk else ''
   if not detail:tag=tag.replace(', ','/')
   values=[tag,field,c['type']+(' ?' if not c['required'] else '')];yy=y+head+i*rowh
   row=SubElement(root,'mxCell',id=ident+'-'+field,value='',vertex='1',parent=ident,style='shape=tableRow;horizontal=0;startSize=0;swimlaneHead=0;swimlaneBody=0;fillColor=none;collapsible=0;points=[[0,0.5],[1,0.5]];portConstraint=eastwest;')
   SubElement(row,'mxGeometry',y=str(head+i*rowh),width=str(nw),height=str(rowh),**{'as':'geometry'});xx=0
   for j,v in enumerate(values):
    size=([11,14,13] if detail else [14,18,14])[j]
    cc=SubElement(root,'mxCell',id=ident+'-'+field+'-c'+str(j),value=v,vertex='1',parent=ident+'-'+field,style=f'shape=partialRectangle;connectable=0;fillColor=none;top=0;left=0;right=0;bottom=0;html=1;fontFamily=Arial;fontSize={size};fontStyle={5 if j==1 and pk else 1 if j==0 else 0};fontColor={"#555555" if j==2 else "#000000"};align={"center" if j==0 else "left"};spacingLeft=4;')
    SubElement(cc,'mxGeometry',x=str(xx),width=str(widths[j]),height=str(rowh),**{'as':'geometry'})
    text(x+xx+(widths[j]/2 if j==0 else 4),yy+20,v,size,pk if j==1 else j==0,j==0,j==2,pk and j==1);xx+=widths[j]
   if i+1<len(cols[name]) and pk and cols[name][i+1]['name'] not in t['pk']:line([(x,yy+rowh),(x+nw,yy+rowh)])
 text(20,36,'Все поля таблиц Supabase' if detail else 'ER модель LAST PAYCHECK',26,True)
 text(20,height-34,'PK — первичный ключ; FK — внешний ключ. Пунктир — FK версии. Полный составной FK указан в подсказке связи.',15)
 text(20,height-12,'Главная схема содержит ключи; вкладка «Все поля» — полный состав таблиц. auth.users управляется Supabase Auth.',15)
 svg.append('</svg>')
 if not detail:(OUT/'backend-er.svg').write_text('\n'.join(svg),encoding='utf-8')
build();build(True)
ElementTree(mx).write(OUT/'backend-er.drawio',encoding='utf-8',xml_declaration=True)
mermaid=['erDiagram']
for name,t in tables.items():
 mermaid+=['    '+name.replace('.','_')+' {']
 for c in t['cols']:
  key=' PK,FK' if c['name'] in t['pk'] and c['name'] in t['fk'] else ' PK' if c['name'] in t['pk'] else ' FK' if c['name'] in t['fk'] else ''
  mermaid.append('        '+c['type'].split('(')[0]+' '+c['name']+key)
 mermaid+=['    }']
for f in fks:
 optional=any(not next(c['required'] for c in tables[f['child']]['cols'] if c['name']==k) for k in f['childKeys'])
 single=set(f['childKeys'])==tables[f['child']]['pk']
 mermaid.append('    '+f['parent'].replace('.','_')+(' |o' if optional else ' ||')+('--o| ' if single else '--o{ ')+f['child'].replace('.','_')+' : references')
(OUT/'backend-er.mmd').write_text('\n'.join(mermaid)+'\n',encoding='utf-8')
print('19 entities including auth.users; 22 actual FKs; two editable pages')

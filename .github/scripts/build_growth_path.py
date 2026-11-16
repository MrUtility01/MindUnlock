#!/usr/bin/env python3
import base64, datetime, gzip, hashlib, json, math, mimetypes, re, shutil
from collections import defaultdict
from pathlib import Path

ROOT=Path("joplin-extracted/MindUnlock_IT_Joplin_Knowledge_Base_FA")
IMPORTER=Path("joplin-extracted/joplin_importer")
LIB=Path("content/library/growth-path")
ASSETS=LIB/"assets"
REPORT_PATH=Path("docs/GROWTH_PATH_CONVERSION_REPORT_FA.md")

def sha(b): return hashlib.sha256(b).hexdigest()
def natural(s): return [int(x) if x.isdigit() else x.casefold() for x in re.split(r"(\d+)",s)]
def group_for(rel):
    p=rel.split("/"); top=p[0]
    if re.match(r"^\d{2} - ",top) and top[:2] in {f"{i:02d}" for i in range(1,17)}:
        if len(p)>=3 and re.match(r"^C\d{2} - ",p[2]): return ("chapter",p[2],top,p[1])
        return ("domain","domain-overviews","راهنمای حوزه‌ها","")
    if top=="17 - تخصص‌های تکمیلی" and len(p)>=2 and re.match(r"^X\d{2} - ",p[1]): return ("specialty",p[1],top,"")
    if top=="17 - پروژه‌های تخصصی عملیاتی": return ("operations","operations","پروژه‌های عملیاتی","")
    if top=="17 - پیوست‌های پروژه‌ای و تحویل واقعی": return ("appendix","appendix","پیوست‌های پروژه‌ای","")
    return ("navigation","navigation","راهنما، داشبورد و قالب‌ها","")
def extra_type(rel,suffix):
    n=Path(rel).stem
    if suffix in (".jpg",".jpeg",".png",".webp"): return "attachment"
    if suffix==".py": return "code"
    if suffix in (".csv",".json"): return "reference"
    if any(x in n for x in ("README","راهنما","منابع","MANIFEST")): return "reference"
    if "سناریو" in n: return "scenario"
    if "دستور" in n or "CLI" in n: return "commands"
    if "آزمایشگاه" in n or "Lab" in n: return "lab"
    if "چک‌لیست" in n or "Runbook" in n or "عیب‌یابی" in n: return "troubleshooting"
    if "پروژه" in n: return "project"
    return "lesson"

def classify_content_kind(rel,title,content,source_type):
    identity=(Path(rel).stem+" "+title).casefold()
    patterns=[
        ("question",r"سؤال|سوال|پرسش|question|quiz|q[ ]*&[ ]*a"),
        ("exam",r"آزمون|امتحان|ارزیابی|exam|test"),
        ("lab",r"آزمایشگاه|لابراتوار|lab|کارگاه عملی|سناریوی عملی"),
        ("troubleshooting",r"عیب.?یابی|رفع.?اشکال|troubleshoot|runbook|incident"),
        ("checklist",r"چک.?لیست|checklist|pre.?flight"),
        ("tip",r"نکته|ترفند|best.?practice|tips?"),
        ("command",r"دستور(?:ات)?|فرمان|command|cli|terminal"),
        ("example",r"مثال|نمونه|example|نمونه.?پیکربندی"),
        ("exercise",r"تمرین|exercise|practice|کار.?عملی"),
        ("project",r"پروژه|project|تحویل.?واقعی"),
        ("glossary",r"واژه.?نامه|اصطلاحات|glossary"),
        ("reference",r"مرجع|reference|cheat.?sheet"),
    ]
    for kind,pattern in patterns:
        if re.search(pattern,identity,re.I): return kind
    source=str(source_type or "").casefold()
    if source in ("todo","task","2"): return "checklist"
    if source in ("code","script"): return "command"
    return "lesson" if content.strip() else "note"

def split_markdown_sections(title,content):
    headings=list(re.finditer(r"(?m)^(#{1,6})\\s+(.+?)\\s*$",content))
    if len(headings)<=1: return [(title,content,0)]
    sections=[]
    prefix=content[:headings[0].start()].strip()
    for index,match in enumerate(headings):
        end=headings[index+1].start() if index+1<len(headings) else len(content)
        block=content[match.start():end]
        if index==0 and prefix: block=prefix+"\\n\\n"+block
        sections.append((match.group(2).strip(),block,len(match.group(1))))
    return sections

def main():
    if not ROOT.is_dir() or not IMPORTER.is_dir(): raise SystemExit("Joplin source folders were not extracted.")
    src_manifest=json.loads((ROOT/"MANIFEST.json").read_text(encoding="utf-8"))
    src_by_path={x["path"]:x for x in src_manifest}
    files=sorted([p for p in ROOT.rglob("*") if p.is_file()]+[p for p in IMPORTER.rglob("*") if p.is_file()])
    groups=defaultdict(list); md_count=0; substantial=0; draft_count=0; category_counts=defaultdict(int)
    for p in files:
        rel=p.relative_to(ROOT).as_posix() if p.is_relative_to(ROOT) else "joplin_importer/"+p.relative_to(IMPORTER).as_posix()
        ext=p.suffix.lower(); entry=src_by_path.get(rel); kind,key,domain,sub=group_for(rel); data=p.read_bytes()
        if entry: uid,typ,title=entry["id"],entry["type"],entry["title"]
        else:
            uid="extra-"+sha(rel.encode())[:20]; typ=extra_type(rel,ext)
            if ext in (".jpg",".jpeg",".png",".webp"): title="پیوست تصویری: "+p.name
            elif ext==".py": title="کد ابزار واردسازی Joplin: "+p.name
            elif ext==".csv": title="فهرست CSV منبع: "+p.name
            elif ext==".json": title="فهرست و فراداده منبع: "+p.name
            else:
                t=data.decode("utf-8-sig",errors="replace")
                title=next((x.strip().lstrip("#").strip() for x in t.splitlines() if x.strip()),p.stem)[:180]
        meta={"source_path":rel,"source_uid":uid,"source_type":typ,"source_format":ext.lstrip(".") or "binary","language":"fa" if ext in (".md",".csv",".json",".py") else "und","import_origin":"Joplin Markdown Directory export"}
        if entry: meta.update({"source_manifest_type":entry["type"],"stable_id":entry["id"]})
        content_kind=typ
        source_status="verified"
        if ext in (".md",".csv",".json",".py"):
            content=data.decode("utf-8-sig",errors="replace")
            if ext==".md":
                md_count+=1
                stripped=re.sub(r"(?m)^#{1,6}.*$","",content)
                stripped=re.sub(r"(?m)^\s*[-*]\s*(?:\[[ xX]\]\s*)?$","",stripped)
                stripped=re.sub(r"(?m)^\s*[-*]\s*$","",stripped)
                stripped=re.sub(r"(?m)^\s*\|.*\|\s*$","",stripped)
                stripped=re.sub(r"<!--.*?-->","",stripped,flags=re.S)
                meaningful=re.sub(r"[\s*_>#|\-]","",stripped)
                content_kind=classify_content_kind(rel,title,content,typ)
                is_substantive=len(meaningful)>180
                meta["content_completeness"]="substantive" if is_substantive else "scaffold_or_short"
                meta["content_kind"]=content_kind
                if is_substantive: substantial+=1
                source_status="verified" if is_substantive or (content_kind not in ("lesson","note") and len(meaningful)>80) else "draft"
                meta["review_status"]="ready" if source_status=="verified" else "template_or_incomplete"
        else:
            ASSETS.mkdir(parents=True,exist_ok=True); shutil.copy2(p,ASSETS/p.name)
            asset="content/library/growth-path/assets/"+p.name
            meta.update({"asset_mime_type":mimetypes.guess_type(p.name)[0] or "application/octet-stream","asset_size_bytes":len(data),"asset_sha256":sha(data),"asset_path":asset})
            content=f"![{p.name}](https://raw.githubusercontent.com/MrUtility01/MindUnlock/main/{asset})\n\nفایل تصویری اصلی در مخزن دوره نگهداری می‌شود."
                source_items=[]
        if ext==".md":
            sections=split_markdown_sections(title,content)
            for section_index,(section_heading,section_content,section_level) in enumerate(sections):
                section_kind=classify_content_kind(rel+" "+section_heading,section_heading,section_content,typ)
                section_plain=re.sub(r"(?m)^#{1,6}.*$","",section_content)
                section_plain=re.sub(r"<!--.*?-->","",section_plain,flags=re.S)
                section_meaningful=re.sub(r"[\s*_>#|\-]","",section_plain)
                section_status="verified" if len(section_meaningful)>180 or (section_kind not in ("lesson","note") and len(section_meaningful)>80) else "draft"
                section_meta=dict(meta)
                section_meta.update({"content_kind":section_kind,"section_index":section_index+1,"section_heading":section_heading,"section_level":section_level,"source_note_title":title,"review_status":"ready" if section_status=="verified" else "template_or_incomplete"})
                section_uid=uid if section_index==0 else uid+"-section-"+str(section_index+1).zfill(3)
                section_title=title if len(sections)==1 or section_heading.casefold()==title.casefold() else title+" — "+section_heading
                source_items.append((section_uid,section_kind,section_title,section_content,section_status,section_meta,section_index))
                category_counts[section_kind]+=1
                if section_status=="draft": draft_count+=1
        else:
            source_items.append((uid,content_kind or "note",title,content,source_status,meta,0))
        for section_uid,section_kind,section_title,section_content,section_status,section_meta,section_index in source_items:
            row_path=rel+"#"+str(section_index).zfill(4)
            groups[(kind,key)].append((row_path,{"source_uid":section_uid,"type":section_kind or "note","source_status":section_status,"title":section_title,"content":section_content,"order":0,"metadata":section_meta}))
    packages=[]
    def add(key,title,group,order,rows,desc):
        rows.sort(key=lambda x:natural(x[0])); items=[]
        for i,(_,item) in enumerate(rows,1): item["order"]=i; items.append(item)
        packages.append({"chapter":{"id":"technology-path-"+key,"title":title,"order":order,"group":group,"description":desc},"items":items})
    add("navigation","راهنما، داشبورد و قالب‌های مسیر رشد","راهنما",1,groups.get(("navigation","navigation"),[]),"داشبورد، راهنمای ورود، قالب‌ها، manifest و ابزارهای همراه منبع؛ مسیر اصلی در metadata حفظ شده است.")
    add("domain-overviews","نقشه ۱۶ حوزه اصلی فناوری","نقشه راه",2,groups.get(("domain","domain-overviews"),[]),"نمای کلی ۱۶ حوزه اصلی؛ فصل‌ها در بسته‌های مستقل ارائه شده‌اند.")
    chapters=sorted([(k,v) for (t,k),v in groups.items() if t=="chapter"],key=lambda x:natural(x[0]))
    if len(chapters)!=68: raise SystemExit(f"Expected 68 chapters, got {len(chapters)}")
    for order,(key,rows) in enumerate(chapters,3):
        domain=rows[0][0].split("/")[0]; sub=rows[0][0].split("/")[1]
        add(re.match(r"(C\d{2})",key).group(1),key,domain,order,rows,"یادداشت‌های فصل؛ مسیر و شناسه منبع در metadata حفظ شده است. تخصص: "+sub)
    specs=sorted([(k,v) for (t,k),v in groups.items() if t=="specialty"],key=lambda x:natural(x[0]))
    if len(specs)!=3: raise SystemExit(f"Expected 3 specialties, got {len(specs)}")
    for order,(key,rows) in enumerate(specs,71): add(key.split(" - ")[0],key,"تخصص‌های تکمیلی",order,rows,"تمام یادداشت‌های تخصص تکمیلی و فصل‌های داخلی آن.")
    add("operations","پروژه جامع عملیاتی Issabel، Cisco و MikroTik","پروژه‌های عملیاتی",74,groups.get(("operations","operations"),[]),"پروژه عملیاتی یکپارچه شبکه و تلفن سازمانی؛ هشدارهای ایمنی منبع حفظ شده‌اند.")
    add("field-appendix","پیوست تحویل واقعی و پروژه حسابداری پدیده","پروژه‌های میدانی",75,groups.get(("appendix","appendix"),[]),"پیوست‌های کشف و تحویل زیرساخت، تصاویر، شواهد، ESXi، MikroTik، Cisco، Issabel و Runbook.")
    items=[it for pkg in packages for it in pkg["items"]]
    if len(packages)!=75 or len(items)!=len(files): raise SystemExit(f"Count mismatch: packages={len(packages)}, items={len(items)}, files={len(files)}")
    if len({x["source_uid"] for x in items})!=len(items): raise SystemExit("Duplicate source_uid")
    if md_count!=2804: raise SystemExit(f"Unexpected Markdown count: {md_count}")
    now=datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00","Z")
    bundle={"schema_version":1,"course":{"id":"technology-path","title":"مسیر رشد","language":"fa","description":"تبدیل وفادار ساختار Joplin به بسته‌های بومی MindUnlock؛ متن منبع بدون بازنویسی حفظ شده و وضعیت تکمیل هر یادداشت در metadata مشخص است.","source":"MindUnlock_IT_Joplin_Knowledge_Base_FA","source_format":"Joplin Markdown Directory ZIP","domain_count":16,"numbered_chapter_count":68,"supplemental_specialty_count":3},"domain_count":16,"numbered_chapter_count":68,"supplemental_specialty_count":3,"package_count":75,"content_item_count":len(items),"packages":packages}
    raw=json.dumps(bundle,ensure_ascii=False,separators=(",",":")).encode("utf-8")
    encoded=base64.b64encode(gzip.compress(raw,compresslevel=9,mtime=0)).decode("ascii")
    if len(encoded.encode())>=1_000_000: raise SystemExit(f"Bundle exceeds app limit: {len(encoded)}")
    n=4; size=math.ceil(len(encoded)/n); LIB.mkdir(parents=True,exist_ok=True)
    for old in LIB.glob("complete-course.part-*.b64"): old.unlink()
    for i in range(n):
        chunk=encoded[i*size:(i+1)*size]
        if not chunk: raise SystemExit("Empty chunk")
        (LIB/f"complete-course.part-{i+1:03d}-of-{n:03d}.b64").write_text(chunk,encoding="ascii")
    manifest=json.loads(Path("content/manifest.json").read_text(encoding="utf-8"))
    entry=next((x for x in manifest["items"] if x["id"]=="technology-path-index"),None)
    if entry is None: raise SystemExit("technology-path-index entry not found")
    entry.update({"title":"مسیر رشد","group_name":"مسیر رشد","order_num":0,"path":f"content/library/growth-path/complete-course.part-001-of-{n:03d}.b64","format":"json-gzip-base64-multipart","content_hash":sha(encoded.encode()),"updated_at":now,"schema_version":1})
    manifest["generated_at"]=now
    Path("content/manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    readme=f"""# راهنمای محتوای GitHub در MindUnlock

این پوشه منبع محتوای آموزشی عمومی برنامه است. همگام‌سازی آن مستقل از سرویس LAN ویندوز باقی می‌ماند.

## قرارداد Manifest نسخه ۱

هر ورودی Manifest شامل شناسه پایدار، عنوان، گروه، ترتیب، مسیر، قالب، هش SHA-256 و زمان UTC است. برای بسته چندبخشی، هش از متن Base64 پس از اتصال مرتب بخش‌ها محاسبه می‌شود. فایل JSON فصل شامل شیء chapter و آرایه items است.

## ایمنی همگام‌سازی

برنامه پیش از واردسازی مسیر، پسوند، اندازه، ساختار و SHA-256 را کنترل می‌کند. تغییر محلی، تعارض دوطرفه و فصل محلی فاقد سابقه همگام‌سازی خودکار جایگزین نمی‌شوند. حذف ورودی از Manifest نیز داده محلی را حذف نمی‌کند.

## دوره مسیر رشد

- ۱۶ حوزه اصلی، ۶۸ فصل شماره‌دار و ۳ تخصص تکمیلی
- ۷۵ بسته مستقل و {len(items):,} آیتم منبع
- {md_count:,} فایل Markdown و ۶ فایل پشتیبان شامل دو تصویر، دو JSON، یک CSV و یک اسکریپت Python
- {len(src_manifest):,} یادداشت در Manifest اصلی Joplin؛ یادداشت‌های تکمیلی پروژه‌های عملیاتی و پیوست تحویل نیز حفظ شده‌اند.
- چهار بخش Base64/GZIP در content/library/growth-path/؛ شناسه Manifest برای حفظ سابقه همگام‌سازی ثابت مانده است.
- شناسه، نوع و مسیر اصلی هر یادداشت در metadata حفظ شده و متن Markdown بدون بازنویسی وارد بسته شده است.
- دو تصویر پروژه در content/library/growth-path/assets/ قرار می‌گیرند.

**وضعیت محتوا:** آرشیو از نظر ساختار و فایل‌ها سالم است، اما همه یادداشت‌ها درس کامل نیستند. بررسی خودکار نشان داد {substantial:,} یادداشت دارای متن نسبتاً پرحجم‌اند؛ بیشتر موارد دیگر قالب، چک‌لیست یا یادداشت کوتاه/قابل‌تکمیل‌اند. متن منبع حفظ شده و محتوای خالی با متن ساختگی پر نشده است.

برنامه هش بسته Base64 پیوسته را کنترل می‌کند، GZIP/Base64 را با سقف حجم امن باز می‌کند و بسته‌ها را به‌صورت تراکنشی در Room وارد می‌کند. همگام‌سازی حذف خودکار ندارد.
"""
    Path("content/README_FA.md").write_text(readme,encoding="utf-8")
    Path("docs").mkdir(exist_ok=True)
    report=f"""# گزارش تبدیل دوره مسیر رشد

- زمان ساخت UTC: {now}
- فایل‌های منبع واردشده: {len(files)}
- فایل‌های Markdown: {md_count}
- رکوردهای Manifest اصلی Joplin: {len(src_manifest)}
- یادداشت‌های دارای متن نسبتاً پرحجم: {substantial}
- آیتم‌های آماده مطالعه: {len([it for it in items if it.get("source_status")=="verified"])}
- قالب‌ها/یادداشت‌های نیازمند تکمیل: {draft_count}
- دسته‌بندی خودکار: {json.dumps(dict(sorted(category_counts.items())),ensure_ascii=False)}
- بسته‌ها: {len(packages)} (۶۸ فصل، ۳ تخصص و ۴ بسته راهنما/حوزه/پروژه)
- آیتم‌ها: {len(items)}
- اندازه JSON بازشده: {len(raw):,} بایت
- اندازه Base64: {len(encoded):,} بایت
- SHA-256 Base64: {sha(encoded.encode())}

متن Markdown بدون بازنویسی نگهداری شده است. شناسه، نوع و مسیر اصلی در metadata هر آیتم قرار دارد. دو تصویر به‌صورت فایل مستقل ذخیره می‌شوند. ساختار آرشیو کامل است؛ با این حال بیشتر یادداشت‌ها قالب یا اسکلت قابل‌تکمیل‌اند. محتوای خالی با متن تولیدی جایگزین نشده است.
"""
    REPORT_PATH.parent.mkdir(parents=True,exist_ok=True)
    REPORT_PATH.write_text(report,encoding="utf-8")
    joined="".join((LIB/f"complete-course.part-{i:03d}-of-{n:03d}.b64").read_text().strip() for i in range(1,n+1))
    decoded=json.loads(gzip.decompress(base64.b64decode(joined)).decode("utf-8"))
    assert decoded["package_count"]==75 and decoded["content_item_count"]==len(items)
    assert len(list(ASSETS.glob("*")))==2
    print(json.dumps({"packages":len(packages),"items":len(items),"markdown":md_count,"substantive":substantial,"encoded_bytes":len(encoded),"sha256":sha(encoded.encode())},ensure_ascii=False))

if __name__=="__main__": main()

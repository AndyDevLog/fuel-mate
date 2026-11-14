"""docs/proposal.md → 공식 접수양식 02의 8개 답변 칸. 01·03·04(개인정보)는 건드리지 않는다.

양식: 공모전 사이트 /downloads/contest-application-consent.docx (저장소에 넣지 않는다)
캡처 폴더: start.jpg, parked.jpg, drive.jpg, navi.jpg, trip.jpg(모두 에뮬레이터 앱 영역 1707×1210, 전체 화면에서 x=853·y=90부터 잘라 냄)

  mkdir -p /tmp/form && unzip -q contest-application-consent.docx -d /tmp/form/unpacked
  python3 docs/proposal/fill_form.py docs/proposal.md /tmp/form/unpacked <캡처 폴더>
  (cd /tmp/form/unpacked && zip -qXr <출력.docx> '[Content_Types].xml' .)

문서 속성의 마지막 편집자·회사 칸은 비운다(블라인드 심사). Word에서 저장하면 Word 계정 이름이 다시 들어갈 수 있으니
제출 전에 Word의 "문서 검사"로 개인 정보를 지운다.
"""
import re
import shutil
import sys
from pathlib import Path
from xml.sax.saxutils import escape

md_path, unpacked, shots = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
doc_path = unpacked / "word" / "document.xml"
x = doc_path.read_text(encoding="utf-8")

# ---- 마크다운 읽기: "## N. 제목" 아래 블록들
md = md_path.read_text(encoding="utf-8")
app_name = re.search(r"^앱명 / 프로젝트명: (.+)$", md, re.M).group(1).strip()
sections: dict[int, list[str]] = {}
cur = None
for line in md.splitlines():
    m = re.match(r"^## (\d)\. ", line)
    if m:
        cur = int(m.group(1))
        sections[cur] = []
    elif cur is not None:
        sections[cur].append(line)

RED = "C00000"


def runs(text: str, bold_all=False) -> str:
    """**굵게**, `코드`(따옴표 없이), [확인 필요](빨강)를 런으로."""
    out = []
    for part in re.split(r"(\*\*[^*]+\*\*|`[^`]+`)", text):
        if not part:
            continue
        bold = bold_all
        if part.startswith("**"):
            part, bold = part[2:-2], True
        elif part.startswith("`"):
            part = part[1:-1]
        color = RED if part.startswith("[") and part.endswith("]") else None
        rpr = ("<w:b/>" if bold else "") + (f'<w:color w:val="{color}"/>' if color else "") + '<w:lang w:eastAsia="ko-KR"/>'
        out.append(f'<w:r><w:rPr>{rpr}</w:rPr><w:t xml:space="preserve">{escape(part)}</w:t></w:r>')
    return "".join(out)


def para(content: str, indent=0, hanging=0, before=0, after=60) -> str:
    ind = f'<w:ind w:left="{indent}" w:hanging="{hanging}"/>' if indent else ""
    return (f'<w:p><w:pPr><w:spacing w:before="{before}" w:after="{after}" w:line="300" w:lineRule="auto"/>{ind}'
            f'<w:rPr><w:lang w:eastAsia="ko-KR"/></w:rPr></w:pPr>{content}</w:p>')


def blocks(lines: list[str]) -> str:
    out = []
    for line in lines:
        s = line.strip()
        if not s:
            continue
        if s.startswith("- "):
            out.append(para(runs("• " + s[2:]), indent=300, hanging=200))
        elif re.match(r"^\d+\. ", s):
            n, rest = s.split(". ", 1)
            out.append(para(runs(f"{n}. " + rest), indent=300, hanging=220))
        elif s.startswith("**") and s.endswith("**") and s.count("**") == 2:
            out.append(para(runs(s), before=120))
        else:
            out.append(para(runs(s)))
    return "".join(out)


# ---- 화면 캡처(7번 칸에 붙임)
IMAGES = [("start.jpg", "첫 화면: 목적지가 없으면 앱이 하는 일과 내비의 바로 가기(집·회사·최근 목적지), [어디로 가세요?]"),
          ("parked.jpg", "추천 카드(정차): 돌아가는 기름값까지 뺀 이득 금액을 가장 크게, 대신 들를 수 있는 곳 표, [들르기]"),
          ("drive.jpg", "주행 화면: 정차 화면과 같은 두 열에서 그림은 멈추고 목록은 빠짐. 이득 금액 큰 글자 한 줄 + 주유소·우회 보조 한 줄 + 들르기 버튼 하나, 오른쪽에 연료와 지금 운행 습관 비용"),
          ("navi.jpg", "들르기 후: 차 안 내비게이션이 추천 주유소를 거치는 경로로 다시 안내"),
          ("trip.jpg", "지난 운행: 습관 때문에 더 쓴 기름값(추정)과 출발~도착 타임라인(급제동·급가속 배지)")]
media = unpacked / "word" / "media"
media.mkdir(exist_ok=True)
rels_path = unpacked / "word" / "_rels" / "document.xml.rels"
rels = rels_path.read_text(encoding="utf-8")
ct_path = unpacked / "[Content_Types].xml"
ct = ct_path.read_text(encoding="utf-8")
if 'Extension="jpg"' not in ct:
    ct = ct.replace("<Default Extension=\"xml\"", '<Default Extension="jpg" ContentType="image/jpeg"/><Default Extension="xml"', 1)
    ct_path.write_text(ct, encoding="utf-8")


def jpeg_size(p: Path) -> tuple[int, int]:
    b = p.read_bytes()
    i = 2
    while i < len(b):
        if b[i] != 0xFF:
            i += 1
            continue
        marker = b[i + 1]
        if marker in (0xC0, 0xC1, 0xC2):
            h = int.from_bytes(b[i + 5:i + 7], "big")
            w = int.from_bytes(b[i + 7:i + 9], "big")
            return w, h
        i += 2 + int.from_bytes(b[i + 2:i + 4], "big")
    raise ValueError(p)


image_xml = []
for n, (name, caption) in enumerate(IMAGES, start=1):
    shutil.copy(shots / name, media / f"proposal-{name}")
    rid = f"rIdProposal{n}"
    if rid not in rels:
        rels = rels.replace("</Relationships>",
                            f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/proposal-{name}"/></Relationships>')
    w, h = jpeg_size(shots / name)
    cx = 5486400  # 6인치
    cy = int(cx * h / w)
    drawing = (
        f'<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="{cx}" cy="{cy}"/>'
        f'<wp:docPr id="{9000 + n}" name="화면 {n}"/>'
        '<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">'
        '<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
        '<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">'
        f'<pic:nvPicPr><pic:cNvPr id="{n}" name="{name}"/><pic:cNvPicPr/></pic:nvPicPr>'
        f'<pic:blipFill><a:blip r:embed="{rid}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
        f'<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm>'
        '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>'
        '</wp:inline></w:drawing></w:r>')
    image_xml.append(para(drawing, before=160, after=40))
    image_xml.append(para(runs(f"그림 {n}. {caption}"), after=60))
rels_path.write_text(rels, encoding="utf-8")

# ---- 답변 칸 채우기: 8개 <w:sdt>가 문항 1~8 순서다. 뒤에서부터 바꿔야 위치가 안 밀린다
sdts = [m for m in re.finditer(r"<w:sdt>.*?</w:sdt>", x, flags=re.S)]
assert len(sdts) == 8, len(sdts)
for idx in range(7, -1, -1):
    m = sdts[idx]
    p_start = x.rfind("<w:p ", 0, m.start())
    p_end = x.find("</w:p>", m.end()) + len("</w:p>")
    body = blocks(sections[idx + 1])
    if idx + 1 == 7:
        body += "".join(image_xml)
    x = x[:p_start] + body + x[p_end:]
    # 긴 답이 다음 쪽으로 넘어갈 수 있게 이 칸의 행 나눔 금지를 푼다
    tr_start = x.rfind("<w:tr ", 0, p_start)
    trpr_end = x.find("</w:trPr>", tr_start)
    x = x[:tr_start] + x[tr_start:trpr_end].replace("<w:cantSplit/>", "") + x[trpr_end:]

# ---- 02의 앱명 줄: "1. 앱 한 줄 소개" 바로 앞의 "프로젝트명" 문단(제목 글자는 런이 쪼개져 있어 쓰지 않는다)
q1 = x.find("1. 앱 한 줄 소개")
assert q1 > 0
name_at = x.rfind("프로젝트명:", 0, q1)
assert name_at > 0
p_start = x.rfind("<w:p ", 0, name_at)
p_end = x.find("</w:p>", name_at)
p = x[p_start:p_end]
PRESERVE = '<w:t xml:space="preserve">'
p = re.sub(r"<w:t[^>]*>\s*프로젝트명: _+</w:t>", lambda mm: PRESERVE + " 프로젝트명: " + escape(app_name) + "</w:t>", p, count=1)
p = re.sub(r"<w:t>_+</w:t>", "<w:t></w:t>", p)
x = x[:p_start] + p + x[p_end:]

doc_path.write_text(x, encoding="utf-8")
print("채움:", app_name, {k: len(v) for k, v in sections.items()})

# ---- 문서 속성: 마지막 편집자·회사 비우기
for name, pattern, empty in [("core.xml", r"<cp:lastModifiedBy>[^<]*</cp:lastModifiedBy>", "<cp:lastModifiedBy></cp:lastModifiedBy>"),
                             ("app.xml", r"<Company>[^<]*</Company>", "<Company></Company>")]:
    path = unpacked / "docProps" / name
    path.write_text(re.sub(pattern, empty, path.read_text(encoding="utf-8")), encoding="utf-8")

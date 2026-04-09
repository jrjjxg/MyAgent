import json
import sys
from pathlib import Path

import fitz
import docx


def configure_stdio():
    for stream_name in ("stdin", "stdout", "stderr"):
        stream = getattr(sys, stream_name, None)
        reconfigure = getattr(stream, "reconfigure", None)
        if callable(reconfigure):
            reconfigure(encoding="utf-8")


def read_request():
    return json.load(sys.stdin)


def write_response(payload):
    json.dump(payload, sys.stdout, ensure_ascii=True)


def extract_document(request):
    input_path = Path(request["input_path"])
    suffix = input_path.suffix.lower()
    if suffix == ".pdf":
        return extract_pdf(input_path)
    if suffix == ".docx":
        return extract_docx(input_path)
    if suffix in {".txt", ".md"}:
        return extract_text(input_path)
    raise ValueError(f"Unsupported document type: {input_path.suffix}")


def extract_pdf(path: Path):
    document = fitz.open(path)
    pages = []
    full_text_parts = []
    for index, page in enumerate(document, start=1):
        text = page.get_text("text")
        pages.append({"pageNumber": index, "text": text})
        full_text_parts.append(text)
    return {
        "kind": "pdf",
        "pageCount": len(pages),
        "fullText": "\n\n".join(full_text_parts).strip(),
        "pages": pages,
    }


def extract_docx(path: Path):
    document = docx.Document(path)
    paragraphs = [paragraph.text for paragraph in document.paragraphs if paragraph.text.strip()]
    text = "\n".join(paragraphs)
    return {
        "kind": "docx",
        "pageCount": 1,
        "fullText": text,
        "pages": [{"pageNumber": 1, "text": text}],
    }


def extract_text(path: Path):
    text = path.read_text(encoding="utf-8", errors="ignore")
    return {
        "kind": path.suffix.lower().lstrip("."),
        "pageCount": 1,
        "fullText": text,
        "pages": [{"pageNumber": 1, "text": text}],
    }


def render_pdf_pages(request):
    input_path = Path(request["input_path"])
    output_dir = Path(request["output_dir"])
    output_dir.mkdir(parents=True, exist_ok=True)
    document = fitz.open(input_path)
    files = []
    for index, page in enumerate(document, start=1):
        pixmap = page.get_pixmap(matrix=fitz.Matrix(1.5, 1.5))
        filename = f"page-{index:03d}.png"
        pixmap.save(output_dir / filename)
        files.append(filename)
    return {"files": files}


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: document_tools.py <tool-name>")
    tool_name = sys.argv[1]
    request = read_request()
    if tool_name == "extract_document":
        write_response(extract_document(request))
        return
    if tool_name == "render_pdf_pages":
        write_response(render_pdf_pages(request))
        return
    raise ValueError(f"Unknown tool: {tool_name}")


if __name__ == "__main__":
    try:
        configure_stdio()
        main()
    except Exception as exc:
        sys.stderr.write(f"{type(exc).__name__}: {exc}")
        sys.exit(1)

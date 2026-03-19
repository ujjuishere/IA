import requests
import re

url = "https://oss-backend.vercel.app/api/anubhav/search"

params = {
    "q": "Amazon",
    "page": 1,
    "limit": 1
}

r = requests.get(url, params=params)

data = r.json()

html = data["articles"][0]["description"]

def extract_questions(html):

    text = re.sub("<.*?>", "\n", html)

    lines = text.split("\n")

    questions = []

    keywords = [
        "what",
        "why",
        "how",
        "explain",
        "difference",
        "design",
        "implement",
        "write",
        "find",
        "reverse",
        "detect",
        "check",
        "create"
    ]

    for line in lines:

        line = line.strip().lower()

        if len(line) < 8:
            continue

        # remove numbering
        line = re.sub(r'^\d+[\).\s]*','',line)

        if "?" in line:
            questions.append(line)
            continue

        for k in keywords:
            if line.startswith(k):
                questions.append(line)
                break

    return questions



qs = extract_questions(html)

print("Questions found:", len(qs))

for q in qs:
    print(q)

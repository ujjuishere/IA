import requests
import json
import re
import time
from collections import defaultdict

BASE_API = "https://oss-backend.vercel.app/api/anubhav"

dataset = defaultdict(list)


question_keywords = [
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
    "create",
    "given"
]


def extract_questions(html):

    # remove html tags
    text = re.sub("<.*?>", "\n", html)

    lines = text.split("\n")

    questions = []

    for line in lines:

        line = line.strip().lower()

        if len(line) < 8:
            continue

        # remove numbering
        line = re.sub(r'^\d+[\).\s]*', '', line)

        # keep first sentence only
        line = line.split(".")[0]

        if len(line) < 8:
            continue

        # detect question mark
        if "?" in line:
            questions.append(line.strip())
            continue

        # detect question keywords
        for k in question_keywords:
            if line.startswith(k):
                questions.append(line.strip())
                break

    return questions


def get_companies():

    print("Fetching company list...")

    r = requests.get(f"{BASE_API}/countCompanies")

    data = r.json()

    companies = [c["company"] for c in data["data"]]

    print("Total companies found:", len(companies))

    return companies


def scrape_company(company):

    print("\nScraping:", company)

    page = 1

    questions = []

    while True:

        r = requests.get(
            f"{BASE_API}/search",
            params={
                "q": company,
                "page": page,
                "limit": 10
            }
        )

        data = r.json()

        blogs = data.get("articles", [])

        if not blogs:
            break

        print("  Page", page, "->", len(blogs), "blogs")

        for blog in blogs:

            desc = blog.get("description", "")

            qs = extract_questions(desc)

            questions.extend(qs)

        page += 1

        time.sleep(0.5)

    return list(set(questions))


def main():

    companies = get_companies()

    for company in companies:

        dataset[company] = scrape_company(company)

        print(company, "->", len(dataset[company]), "questions")

    with open("anubhav_questionss.json", "w", encoding="utf-8") as f:

        json.dump(dataset, f, indent=4)

    print("\nDataset saved to anubhav_questions.json")


if __name__ == "__main__":
    main()
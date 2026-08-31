"""Writes an image-generation prompt for every character in the game's data.

The board answers ten questions about each character, so all ten attributes
have to be visible in the picture. Deriving the prompts from GuessWhoDB.csv
rather than writing them by hand keeps the art and the answers in step.

Run from the repository root:  python3 tools/character-prompts.py
"""
import csv
import pathlib

#Three things here are lessons from the first card rather than taste. The crop
#matters because the board shows these at 100x150, where a face filling half the
#frame leaves eye colour unreadable. The hat clause matters because the board
#asks about hats and hair colour separately, so a black hat over black hair
#leaves a card unable to answer one of its own questions. The clear strip at the
#bottom is where tools/NameCards.java writes the name.
STYLE = ("Soft cel-shaded illustration portrait, front-facing, symmetrical, "
         "head and shoulders filling the frame with the top of the head near the "
         "top edge and the chin around the lower third, plain solid pastel "
         "background, clean line work, large clearly visible eyes, even lighting, "
         "2:3 portrait aspect ratio. Any hat must be a colour that clearly "
         "contrasts with the hair, so both stay readable. Leave the bottom sixth "
         "of the image as plain background, with the shoulders and chest running "
         "all the way down to the bottom edge behind it. "
         "No text, no lettering, no watermark, no border.")

DATA = pathlib.Path("game-core/src/main/resources/data/GuessWhoDB.csv")
TARGET = pathlib.Path("tools/character-prompts.md")


def describe(row):
    """Turns one CSV row into a sentence naming every attribute the board asks about."""
    name, eye, male, light, hair, beard, glasses, teeth, hat, length, pierce = \
        [cell.strip() for cell in row[:11]]
    male = male.upper() == "TRUE"
    hair = hair.lower()

    parts = ["A man" if male else "A woman",
             "with light skin" if light.upper() == "TRUE" else "with dark brown skin",
             f"{eye.lower()} eyes, clearly visible"]

    if length.lower() == "bald":
        parts.append(f"completely bald, with {hair} eyebrows")
    elif length.lower() == "tied up":
        parts.append(f"{hair} hair tied up in a bun")
    elif length.lower() == "long":
        parts.append(f"long {hair} hair past the shoulders")
    else:
        parts.append(f"short {hair} hair")

    #Only worth saying for men: telling a generator a woman is clean-shaven
    #wastes a clause and sometimes confuses the result.
    if beard.upper() == "TRUE":
        parts.append(f"a full {hair} beard and moustache")
    elif male:
        parts.append("clean-shaven, no beard or moustache")

    parts.append("wearing round glasses" if glasses.upper() == "TRUE"
                 else "not wearing glasses")
    parts.append("smiling broadly with teeth showing" if teeth.upper() == "TRUE"
                 else "a closed-mouth smile, no teeth showing")
    parts.append("wearing a hat that covers the top of the head" if hat.upper() == "TRUE"
                 else "no hat or head covering")
    #Large and hooped on purpose. A stud is drawn faithfully and then disappears
    #at 100x150, leaving a card that cannot answer the question the board asks
    #about it.
    parts.append("wearing large hoop earrings, big and clearly visible"
                 if pierce.upper() == "TRUE" else "no earrings, bare earlobes")

    return name, ", ".join(parts) + "."


def main():
    rows = [row for row in csv.reader(DATA.open()) if row and row[0].strip()]

    lines = ["# Character art prompts",
             "",
             "Generated from `GuessWhoDB.csv` by `tools/character-prompts.py`, so each",
             "prompt states exactly the attributes the game answers questions about.",
             "Regenerate this file whenever the character data changes.",
             "",
             "Generate them in a single conversation and keep the style locked to the",
             "first card, or the set ends up in twenty-four different styles. Save the",
             "results as `0.jpg` to `23.jpg` in `tools/portraits`, in the order below,",
             "then run `java tools/NameCards.java` to add the name bands and write the",
             "finished cards into `game-core/src/main/resources/images`.",
             "",
             "The names are stamped from the character data rather than drawn by the",
             "image generator, which spells them wrong often enough to matter across",
             "twenty-four cards and would vary the lettering on every one.",
             "",
             "## Style preamble",
             "",
             "Use this on the first prompt, then \"same style, same framing and scale\"",
             "for the rest.",
             "",
             "```", STYLE, "```", ""]

    for index, row in enumerate(rows):
        name, prompt = describe(row)
        lines += [f"## {index}. {name}", "", "```", prompt, "```", ""]

    TARGET.write_text("\n".join(lines))
    print(f"Wrote {TARGET} for {len(rows)} characters")


if __name__ == "__main__":
    main()

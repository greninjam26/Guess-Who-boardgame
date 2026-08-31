# Generated portraits

Put the raw generated artwork here, named `0` to `23`, in the order
the characters appear in `GuessWhoDB.csv`. PNG, JPEG, GIF and BMP are all
read, and PNG is the one to prefer: it is lossless, so stamping the name does
not compound compression the generator already applied. The prompts in
[../character-prompts.md](../character-prompts.md) are numbered to match.

Then, from the repository root:

    java tools/NameCards.java

That crops each one to the board's 2:3, adds the name band, and writes the
finished cards into `game-core/src/main/resources/images`.

Keep the files here rather than deleting them once the cards are built. They
cannot be regenerated identically, and re-running the stamp on the finished
cards would letter a name over a name.

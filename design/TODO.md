# IDEA: iterative refinement
Using text, so no fancy UI required. "Add a foo class" "merge  class foo and bar"

Problem is cycle is slow, but maybe not if not generating the whole thing. gv display not really deisgned for iteration (not stable)

Note: if we build The Master Repl then some of this just falls out. Should do that.


# Download

Order-0 persistence

DONE, but should have a schema-only version

And maybe an export-to-some-standard format, like RDFS? Or JSON schema. Well that is a whole other bucket of worms.

JSON schema would be esptecially useful for LLM generation, you can pass it those. Hm. 

# Some way to generatae instances

See alzabo/datagen, that should probably be ported here, and exposed somehow

# Size control
eg a slider in number of classes.
Can also be done through "extra" box

# Persistence

See separeate doc

# Ought be able to generate enums

Since they are in the Alzabno spe after all

# Needs some explanatory text


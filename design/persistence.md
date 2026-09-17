# Basic goal

Make generated schemas persistent, 
And thus viewable in a directory and recallable


# Basic problem

Heroku has no long-term persistent filesystem

therrefore: use s3:

generate on local fs and copy/retrieve to s3:

# Directory

Ther's some mininal old code in directory,clj

Maybe use an ag-grid, then it will be sortable.



# UI

## button to save ontology (with maybe fields for name, comments, user)

## directory

## links should be shareable and readable 

eg http://ontogeny.hyperphor.com/genetic_disorders

### which implies that sub-pages should also have decent URLs

http://ontogeny.hyperphor.com/genetic_disorders/mutation

Which might require tweaks to Alzabo schema generation and/or a server in front of the .html files

# Next-level coolness

Indexing by kind names or by full text (eg, like the Alzabo search widget but across all schemas)


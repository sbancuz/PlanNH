# DESIGN DOC

This mod is a NEI plugin that has to do just one thing: Provide a flowchart editor where you can put and chain recipes to visualise long crafting chains like for GTNH

## Features

- Graphical interface to put recipes where you see input/output/machine and time
- zoom
- Arrows to show the flows
- recipes costs (singular and aggregates)
- timings (one cycle takes x seconds, etc..)

## Requirements

- use MUI2 (Use themes)
- use NEI recipe handlers to show recipes
- have the flowchart persistent 
- make flowchart shareable via base64 encoding
- follow standard gtnh mod structure

## GUI

The gui needs to the standard NEI GUI, but in the center there will be the flowchart stuff and on the left the summary (instead of favourites)
Recipes will be picked from the normal item pickers 

#!/bin/bash

#(
#docker run --rm -u `id -u`:`id -g` -p 1313:1313 -v $PWD/../..:/srv/hugo factcast/factcast-hugo hugo server -s factcast-site/documentation-docsy -w --bind 0.0.0.0 --disableFastRender --log --minify --debug --navigateToChanged --templateMetrics --buildDrafts
#)
#


docker run --rm -it \
  -v $PWD/../..:/src \
  -p 1313:1313 \
  klakegg/hugo:0.101.0-ext-debian \
  server -s factcast-site/documentation-docsy -w --bind 0.0.0.0 --disableFastRender --log --minify --debug --navigateToChanged --templateMetrics --buildDrafts

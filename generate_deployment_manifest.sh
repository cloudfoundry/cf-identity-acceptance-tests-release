#! /bin/bash

set -e -u

templates=$(dirname $0)/templates

infrastructure=$1
shift

spiff merge \
  ${templates}/dogs-guvnor.yml \
  ${templates}/${infrastructure}-resource-pools.yml \
  ${templates}/dogs-jobs.yml \
  ${templates}/${infrastructure}-networks.yml \
  "$@"

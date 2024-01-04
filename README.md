# Halstead Complexity Extension for Clojure Code Blocks

This is a Chrome extension written in ClojureScript. It is currently a WIP and therefore breaking changes may occur.

## What This Extension Does

This extension adds Halstead Complexity metrics to ClojureScript code blocks for (currently) two websites, [4clojure.oxal.org](4clojure.oxal.org) and [clojuredocs.org](clojuredocs.org).

## Build Instructions

To build this extension locally:

1. Clone this repo
1. Run `npm install` from the root directory
1. (might not be necessary) Install shadow-cljs globally with `npm install -g shadow-cljs`
1. Build popup.js and content-script.js by running `npx shadow-cljs release popup` and `npx shadow-cljs release content-script` respectively
1. Load the contents of the extension/ folder into Chrome on [chrome://extensions/](chrome://extensions/) via <kbd>Load Unpacked</kbd>

## Testing

Run tests via `npx shadow-cljs watch test`

## Future Goals of This Project

- [x] Implement basic/simple form tests
- [ ] Implemented intermediate/nested form tests
- [ ] Implement advanced tests for specific platform code examples (for example w/ "messy"/"bad code" inputs)
- [ ] Toggle metrics on/off
- [ ] Detect code fences with Clojure/ClojureScript code
- [ ] Metrics for app.klipse.tech editor window
- [ ] Metrics display on hover
- [ ] Sortable code blocks on 4clojure.oxal.org by effort, volume, or difficulty
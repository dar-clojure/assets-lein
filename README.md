# assets-lein

Leiningen plugin for building [assets](https://github.com/dar-clojure/assets)

## Usage

List it as a plugin in `project.clj` and specify `build-dir`

```clojure
(defproject example "0.1.0"
  :plugins [[dar/assets-lein "0.0.1"]]
  :assets {:build-dir "build"})
```

Unlike cljsbuild, it explicitly depends on ClojureScript compiler.
You shouldn't add ClojureScript to your project.clj dependencies.

There are two commands available.

### page

```
lein assets page com/example/foo
```

Compiles `com/example/foo` into optimized signle page
application ready to be hosted from any url in the web.
The output is a `build/out` directory. Always performs fresh,
non-incremental builds. It is slow.

### server

```
lein assets server
```

Starts development server. Each component is served at the corresponding
url. For example, to view `com/example/foo` navigate to
`http://localhost:3000/com/example/foo`. Rebuilds on each request.
You can also append `fresh` query parameter to perform a pristine
build, or `optimize` to optmize ClojureScript.

```
http://localhost:3000/com/example/foo?optmize&fresh
```

## License

Copyright © 2014 Eldar Gabdullin

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

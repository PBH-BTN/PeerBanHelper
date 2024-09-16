# Synology Example Package.

This package is depend on Synology toolkit framework.
This package is a template package, users can modify this package to generate their own packages.

Please setup the toolkit by following [pkgscripts-ng](https://github.com/SynologyOpenSource/pkgscripts-ng)

## Build package.
After setup toolkit environment, you can create package by command:
```bash
pkgscripts-ng/PkgCreate.py [-p {platforms}] -c -v 7.0 ExamplePkg
```
```bash
pkgscripts-ng/PkgCreate.py -p avoton -c -v 7.0 ExamplePkg
pkgscripts-ng/PkgCreate.py -c -v 7.0 ExamplePkg # will generate package for all platforms in build_env
```

You can find generated packages in result_spk directory.

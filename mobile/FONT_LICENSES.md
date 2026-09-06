# Bundled font notes

TripTandem bundles the following fonts for the shared Compose UI so Android and iOS use the same visual system described in [`DESIGN.md`](../DESIGN.md):

- Cabinet Grotesk — Bold and ExtraBold for headings
- Satoshi — Regular, Medium, and Bold for interface text

The files were retrieved from Fontshare on 2026-09-03. Before distributing the app publicly, review the current Fontshare/Indian Type Foundry license terms for each family and retain any required notices in the store or repository materials. These files are intentionally limited to the weights used by the product.

The Android APK keeps a copy under `app/src/main/res/font` so the platform font loader packages the files reliably; the iOS target continues to load the matching files from `shared/src/commonMain/composeResources/font`.

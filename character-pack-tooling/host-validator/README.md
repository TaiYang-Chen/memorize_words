# Basis KTX2 host validator

该程序直接编译仓库中与 Android 客户端相同、固定 commit 的 Basis Universal transcoder。
它会逐层完整转码 ETC2 RGBA、ETC1 RGB 和 ETC1 Alpha；任何一层失败都会以非零状态退出。

```powershell
cmake -S character-pack-tooling/host-validator `
  -B character-pack-tooling/host-validator/build `
  -DCMAKE_BUILD_TYPE=Release
cmake --build character-pack-tooling/host-validator/build --config Release
```

把生成的 `basis_ktx2_validator` 加入 `PATH`，或调用角色包工具时传入
`--validator-path <绝对路径>`。发布构建会校验其版本标识，不能省略该程序。

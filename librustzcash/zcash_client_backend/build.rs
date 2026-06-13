fn main() {
    // src/proto/ 被 .gitignore 忽略，CI 检出后不存在；protobuf-codegen-pure 不会自建输出目录，
    // 必须先创建，否则报 "No such file or directory"。
    std::fs::create_dir_all("src/proto").expect("Failed to create src/proto output dir");
    protobuf_codegen_pure::Codegen::new()
        .out_dir("src/proto")
        .inputs(&["proto/compact_formats.proto"])
        .includes(&["proto"])
        .run()
        .expect("Protobuf codegen failed");
}

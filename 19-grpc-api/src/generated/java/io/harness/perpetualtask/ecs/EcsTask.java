// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: io/harness/perpetualtask/ecs/ecs_task.proto

package io.harness.perpetualtask.ecs;

@javax.annotation.Generated(value = "protoc", comments = "annotations:EcsTask.java.pb.meta")
public final class EcsTask {
  private EcsTask() {}
  public static void registerAllExtensions(com.google.protobuf.ExtensionRegistryLite registry) {}

  public static void registerAllExtensions(com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions((com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors
      .Descriptor internal_static_io_harness_perpetualtask_ecs_EcsPerpetualTaskParams_descriptor;
  static final com.google.protobuf.GeneratedMessageV3
      .FieldAccessorTable internal_static_io_harness_perpetualtask_ecs_EcsPerpetualTaskParams_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor getDescriptor() {
    return descriptor;
  }
  private static com.google.protobuf.Descriptors.FileDescriptor descriptor;
  static {
    java.lang.String[] descriptorData = {"\n+io/harness/perpetualtask/ecs/ecs_task."
        + "proto\022\034io.harness.perpetualtask.ecs\"\225\001\n\026"
        + "EcsPerpetualTaskParams\022\024\n\014cluster_name\030\001"
        + " \001(\t\022\016\n\006region\030\002 \001(\t\022\022\n\naws_config\030\003 \001(\014"
        + "\022\031\n\021encryption_detail\030\004 \001(\014\022\022\n\ncluster_i"
        + "d\030\005 \001(\t\022\022\n\nsetting_id\030\006 \001(\tB\002P\001b\006proto3"};
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
        new com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner() {
          public com.google.protobuf.ExtensionRegistry assignDescriptors(
              com.google.protobuf.Descriptors.FileDescriptor root) {
            descriptor = root;
            return null;
          }
        };
    com.google.protobuf.Descriptors.FileDescriptor.internalBuildGeneratedFileFrom(
        descriptorData, new com.google.protobuf.Descriptors.FileDescriptor[] {}, assigner);
    internal_static_io_harness_perpetualtask_ecs_EcsPerpetualTaskParams_descriptor =
        getDescriptor().getMessageTypes().get(0);
    internal_static_io_harness_perpetualtask_ecs_EcsPerpetualTaskParams_fieldAccessorTable =
        new com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
            internal_static_io_harness_perpetualtask_ecs_EcsPerpetualTaskParams_descriptor,
            new java.lang.String[] {
                "ClusterName",
                "Region",
                "AwsConfig",
                "EncryptionDetail",
                "ClusterId",
                "SettingId",
            });
  }

  // @@protoc_insertion_point(outer_class_scope)
}

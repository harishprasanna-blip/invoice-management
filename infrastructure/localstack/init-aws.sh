#!/bin/bash

# LocalStack initialization script for AWS S3 buckets
# Creates necessary S3 buckets for invoice document storage

echo "Initializing LocalStack AWS resources..."

# Wait for LocalStack to be ready
sleep 5

# Create S3 bucket for invoice documents
awslocal s3 mb s3://invoice-documents || echo "Bucket already exists"

# Enable versioning on the bucket (for document history)
awslocal s3api put-bucket-versioning \
  --bucket invoice-documents \
  --versioning-configuration Status=Enabled

# Set bucket lifecycle policy (simulate S3 Glacier for archival)
awslocal s3api put-bucket-lifecycle-configuration \
  --bucket invoice-documents \
  --lifecycle-configuration '{
    "Rules": [
      {
        "Id": "ArchiveOldInvoices",
        "Status": "Enabled",
        "Filter": {
          "Prefix": "archived/"
        },
        "Transitions": [
          {
            "Days": 365,
            "StorageClass": "GLACIER"
          }
        ]
      },
      {
        "Id": "DeleteVeryOldInvoices",
        "Status": "Enabled",
        "Filter": {
          "Prefix": "archived/"
        },
        "Expiration": {
          "Days": 3650
        }
      }
    ]
  }'

# Create folder structure
awslocal s3api put-object --bucket invoice-documents --key tenants/
awslocal s3api put-object --bucket invoice-documents --key archived/

echo "S3 bucket 'invoice-documents' created and configured successfully"

# List buckets to confirm
awslocal s3 ls

echo "LocalStack initialization complete!"

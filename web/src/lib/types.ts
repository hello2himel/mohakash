/**
 * Types describing the public JSON API documents.
 * The schema version is `1.0.0`; see `/api` for the human-readable version.
 */

export type MediaType = 'image' | 'video' | 'other';
export type ImageStatus = 'ok' | 'placeholder' | 'missing';

export interface ApodRecord {
  schema_version: string;
  date: string;
  title: string;
  explanation: string;
  media_type: MediaType;
  image_url: string | null;
  hd_image_url: string | null;
  thumbnail_url: string | null;
  apod_url: string;
  copyright: string | null;
  credit: string | null;
  alt_text: string | null;
  source: string;
  source_id: string;
  source_media_type: string;
  image_status: ImageStatus;
  updated_at: string;
  generated_at?: string;
  last_successful_sync?: string;
  latest_available_date?: string;
  record_count?: number;
  source_status?: string;
  api_base_url?: string;
}

export interface ArchiveIndexEntry {
  date: string;
  title: string;
  media_type: MediaType;
  thumbnail_url: string | null;
  image_url: string | null;
  apod_url: string;
  copyright: string | null;
  detail_path: string;
}

export interface ArchiveIndex {
  schema_version: string;
  generated_at: string;
  count: number;
  api_base_url: string;
  entries: ArchiveIndexEntry[];
}

export interface ArchiveYear {
  schema_version: string;
  year: number;
  generated_at: string;
  count: number;
  records: ApodRecord[];
}

export interface StatusDocument {
  schema_version: string;
  generated_at: string;
  last_successful_sync: string;
  last_attempt: string;
  latest_available_date: string;
  archive_start_date: string;
  archive_end_date: string;
  record_count: number;
  source_status: string;
  source_name: string;
  source_endpoint: string;
  years: number[];
  api_base_url: string;
  website_base_url: string;
}

/** A record-ish object with only the fields the UI needs. */
export interface RecordLike {
  date: string;
  title: string;
  media_type: MediaType;
  image_url?: string | null;
  hd_image_url?: string | null;
  thumbnail_url?: string | null;
  image_status?: ImageStatus;
  alt_text?: string | null;
  copyright?: string | null;
  credit?: string | null;
  apod_url?: string;
  explanation?: string;
}

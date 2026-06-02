import { getApiMembers, getApiMembersId, postApiMembersIdPhoto } from "./generated";
import type { MemberDetail, MemberStatus, MemberSummary } from "./model";

export type { MemberDetail, MemberStatus, MemberSummary };
export type { Binome } from "./model/binome";
export type { NextShift } from "./model/nextShift";

export async function searchMembers(q: string, signal?: AbortSignal): Promise<MemberSummary[]> {
  const resp = await getApiMembers({ q }, { signal });
  return resp.data ?? [];
}

export async function getMember(id: number, signal?: AbortSignal): Promise<MemberDetail> {
  const resp = await getApiMembersId(id, { signal });
  return resp.data;
}

/**
 * Uploads a photo (data URI or raw base64) for a member and returns the refreshed detail.
 * The fetcher throws on non-2xx, so a resolved value always carries the updated member.
 */
export async function uploadMemberPhoto(
  id: number,
  photo: string,
  signal?: AbortSignal,
): Promise<MemberDetail> {
  const resp = await postApiMembersIdPhoto(id, { photo }, { signal });
  return resp.data as MemberDetail;
}

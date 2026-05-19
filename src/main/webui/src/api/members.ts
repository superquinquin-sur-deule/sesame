/**
 * Application-facing wrappers around the orval-generated client. Keeps the
 * unwrapped DTOs (the generated client returns `{status, data, headers}`)
 * and exposes a stable signature the screens can call.
 */
import { getApiMembers, getApiMembersId } from "./generated";
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

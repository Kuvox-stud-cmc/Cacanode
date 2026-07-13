"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import toast from "react-hot-toast";
import { AlertCircle, Mail, RefreshCw, UserPlus, Users } from "lucide-react";
import { useApiClient } from "@/hooks/useApiClient";
import { useAuthStore } from "@/components/providers/StoreProvider";
import {
  cancelTeamInvitation,
  getTeamDirectory,
  inviteTeamMember,
  resendTeamInvitation,
  updateTeamMemberRole,
  updateTeamMemberStatus,
} from "@/lib/users-api";
import type { TeamDirectory, TeamMember, UserRole, UserStatus } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent } from "@/components/ui/card";

const inviteSchema = z.object({
  email: z.string().email("Enter a valid email address"),
  role: z.enum(["TENANT_ADMIN", "USER"]),
});
type InviteForm = z.infer<typeof inviteSchema>;

const roleLabel = (role: UserRole) => role === "TENANT_ADMIN" ? "Tenant Admin" : "User";
const formatDate = (value: string) => new Date(value).toLocaleDateString(undefined, {
  month: "short", day: "numeric", year: "numeric",
});
const initials = (name: string) => name.split(" ").filter(Boolean).map(part => part[0]).join("").toUpperCase().slice(0, 2) || "?";

function StatusBadge({ status }: { status: string }) {
  const active = status === "ACTIVE";
  const pending = status === "PENDING";
  return <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${
    active ? "bg-green-100 text-green-800" : pending ? "bg-amber-100 text-amber-800" : "bg-slate-100 text-slate-600"
  }`}>{status.charAt(0) + status.slice(1).toLowerCase()}</span>;
}

function TableSkeleton() {
  return <div className="space-y-4 p-5">{Array.from({ length: 4 }).map((_, index) => (
    <div key={index} className="flex items-center gap-4"><Skeleton className="size-8 rounded-full" /><Skeleton className="h-5 flex-1" /><Skeleton className="h-5 w-28" /><Skeleton className="h-5 w-24" /></div>
  ))}</div>;
}

export default function UsersPage() {
  const { request } = useApiClient();
  const authUser = useAuthStore(state => state.user);
  const [directory, setDirectory] = useState<TeamDirectory>({ members: [], invitations: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showInvite, setShowInvite] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const isAdmin = authUser?.role === "TENANT_ADMIN";
  const activeAdminCount = useMemo(() => directory.members.filter(member => member.role === "TENANT_ADMIN" && member.status === "ACTIVE").length, [directory.members]);

  const loadDirectory = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDirectory(await getTeamDirectory(request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to load the team directory");
    } finally {
      setLoading(false);
    }
  }, [request]);

  useEffect(() => {
    const task = window.setTimeout(() => { void loadDirectory(); }, 0);
    return () => window.clearTimeout(task);
  }, [loadDirectory]);

  const { register, handleSubmit, setValue, reset, formState: { errors, isSubmitting } } = useForm<InviteForm>({
    resolver: zodResolver(inviteSchema), defaultValues: { role: "USER" },
  });

  const onInvite = async (values: InviteForm) => {
    try {
      await inviteTeamMember(request, values.email, values.role);
      toast.success(`Invitation sent to ${values.email}`);
      reset(); setShowInvite(false); await loadDirectory();
    } catch (cause) { toast.error(cause instanceof Error ? cause.message : "Unable to send invitation"); }
  };

  const updateMember = (updated: TeamMember) => setDirectory(current => ({
    ...current, members: current.members.map(member => member.id === updated.id ? updated : member),
  }));

  const changeRole = async (member: TeamMember, role: UserRole) => {
    setBusyId(member.id);
    try { updateMember(await updateTeamMemberRole(request, member.id, role)); toast.success("Role updated"); }
    catch (cause) { toast.error(cause instanceof Error ? cause.message : "Unable to update role"); }
    finally { setBusyId(null); }
  };

  const changeStatus = async (member: TeamMember) => {
    const status: UserStatus = member.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    if (status === "INACTIVE" && !window.confirm(`Deactivate ${member.fullName || member.email}? They will be signed out immediately.`)) return;
    setBusyId(member.id);
    try { updateMember(await updateTeamMemberStatus(request, member.id, status)); toast.success(status === "ACTIVE" ? "User reactivated" : "User deactivated"); }
    catch (cause) { toast.error(cause instanceof Error ? cause.message : "Unable to update status"); }
    finally { setBusyId(null); }
  };

  const resend = async (id: string) => {
    setBusyId(id);
    try { await resendTeamInvitation(request, id); toast.success("Invitation resent"); await loadDirectory(); }
    catch (cause) { toast.error(cause instanceof Error ? cause.message : "Unable to resend invitation"); }
    finally { setBusyId(null); }
  };

  const cancel = async (id: string, email: string) => {
    if (!window.confirm(`Cancel the invitation for ${email}?`)) return;
    setBusyId(id);
    try { await cancelTeamInvitation(request, id); toast.success("Invitation cancelled"); setDirectory(current => ({ ...current, invitations: current.invitations.filter(item => item.id !== id) })); }
    catch (cause) { toast.error(cause instanceof Error ? cause.message : "Unable to cancel invitation"); }
    finally { setBusyId(null); }
  };

  return <div className="space-y-6">
    <div className="flex items-center justify-between gap-4">
      <div><h2 className="text-xl font-semibold text-slate-800">Team Members</h2><p className="mt-1 text-sm text-slate-500">Manage members and pending invitations for your workspace.</p></div>
      {isAdmin && <Button size="sm" onClick={() => setShowInvite(true)} className="gap-1.5 bg-indigo-600 text-white hover:bg-indigo-700"><UserPlus className="size-4" />Invite User</Button>}
    </div>

    {error && <div role="alert" className="flex items-center justify-between rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800"><span className="flex items-center gap-2"><AlertCircle className="size-4" />{error}</span><Button size="sm" variant="outline" onClick={() => void loadDirectory()}>Retry</Button></div>}

    <Card>{loading ? <TableSkeleton /> : directory.members.length + directory.invitations.length === 0 ? <CardContent className="py-16 text-center"><Users className="mx-auto mb-3 size-10 text-slate-300" /><p className="font-medium text-slate-500">No team members yet</p>{isAdmin && <p className="mt-1 text-sm text-slate-400">Invite your first team member to get started.</p>}</CardContent> : <Table>
      <TableHeader><TableRow><TableHead>Member</TableHead><TableHead>Role</TableHead><TableHead>Status</TableHead><TableHead>Joined / Expires</TableHead>{isAdmin && <TableHead className="text-right">Actions</TableHead>}</TableRow></TableHeader>
      <TableBody>
        {directory.members.map(member => {
          const finalAdmin = member.role === "TENANT_ADMIN" && member.status === "ACTIVE" && activeAdminCount === 1;
          const protectedAction = member.currentUser || finalAdmin;
          return <TableRow key={`member-${member.id}`}>
            <TableCell><div className="flex items-center gap-3"><Avatar className="size-9"><AvatarFallback className="bg-indigo-100 text-xs text-indigo-700">{initials(member.fullName || member.email)}</AvatarFallback></Avatar><div><p className="font-medium text-slate-800">{member.fullName || "Unnamed user"}{member.currentUser && <span className="ml-2 text-xs font-normal text-slate-400">You</span>}</p><p className="text-sm text-slate-500">{member.email}</p></div></div></TableCell>
            <TableCell>{isAdmin ? <Select value={member.role} disabled={busyId === member.id || protectedAction} onValueChange={value => void changeRole(member, value as UserRole)}><SelectTrigger className="w-36"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="TENANT_ADMIN">Tenant Admin</SelectItem><SelectItem value="USER">User</SelectItem></SelectContent></Select> : <span className="text-sm">{roleLabel(member.role)}</span>}</TableCell>
            <TableCell><StatusBadge status={member.status} /></TableCell><TableCell className="text-sm text-slate-500">{formatDate(member.joinedAt)}</TableCell>
            {isAdmin && <TableCell className="text-right"><Button size="sm" variant="outline" disabled={busyId === member.id || (member.status === "ACTIVE" && protectedAction)} onClick={() => void changeStatus(member)}>{member.status === "ACTIVE" ? "Deactivate" : "Reactivate"}</Button></TableCell>}
          </TableRow>;
        })}
        {directory.invitations.map(invitation => <TableRow key={`invite-${invitation.id}`}>
          <TableCell><div className="flex items-center gap-3"><div className="flex size-9 items-center justify-center rounded-full bg-amber-50"><Mail className="size-4 text-amber-600" /></div><div><p className="font-medium text-slate-800">Pending invitation</p><p className="text-sm text-slate-500">{invitation.email}</p></div></div></TableCell>
          <TableCell className="text-sm">{roleLabel(invitation.role)}</TableCell><TableCell><StatusBadge status={invitation.status} /></TableCell><TableCell className="text-sm text-slate-500">Expires {formatDate(invitation.expiresAt)}</TableCell>
          {isAdmin && <TableCell className="space-x-2 text-right"><Button size="sm" variant="outline" disabled={busyId === invitation.id} onClick={() => void resend(invitation.id)}><RefreshCw className="mr-1 size-3.5" />Resend</Button>{invitation.status === "PENDING" && <Button size="sm" variant="outline" disabled={busyId === invitation.id} onClick={() => void cancel(invitation.id, invitation.email)}>Cancel</Button>}</TableCell>}
        </TableRow>)}
      </TableBody>
    </Table>}</Card>

    <Dialog open={showInvite} onOpenChange={setShowInvite}><DialogContent><DialogHeader><DialogTitle>Invite team member</DialogTitle></DialogHeader><form onSubmit={handleSubmit(onInvite)} className="space-y-4"><div className="space-y-1.5"><Label htmlFor="inviteEmail">Email address</Label><Input id="inviteEmail" type="email" placeholder="colleague@company.com" {...register("email")} />{errors.email && <p className="text-xs text-red-600">{errors.email.message}</p>}</div><div className="space-y-1.5"><Label>Role</Label><Select defaultValue="USER" onValueChange={value => setValue("role", value as UserRole)}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="TENANT_ADMIN">Tenant Admin</SelectItem><SelectItem value="USER">User</SelectItem></SelectContent></Select></div><DialogFooter><Button variant="outline" type="button" onClick={() => setShowInvite(false)}>Cancel</Button><Button type="submit" disabled={isSubmitting} className="bg-indigo-600 text-white hover:bg-indigo-700">{isSubmitting ? "Sending..." : "Send invitation"}</Button></DialogFooter></form></DialogContent></Dialog>
  </div>;
}

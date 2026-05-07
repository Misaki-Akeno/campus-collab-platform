export default function ClubDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  return (
    <div className="p-6">
      <p className="text-muted-foreground">社团详情页（Phase B 实现）</p>
    </div>
  );
}

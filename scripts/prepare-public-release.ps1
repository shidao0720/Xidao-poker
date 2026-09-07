# Run only from the private development repository after committing changes.
# No network access, push, deletion, or history rewrite in the source repository.
$ErrorActionPreference = 'Stop'

function Invoke-Git {
    param([string[]]$GitArgs)
    $result = & git @GitArgs
    if ($LASTEXITCODE -ne 0) { throw "Git failed: $($GitArgs[0]) (exit $LASTEXITCODE)" }
    return $result
}

$source = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$head = Invoke-Git -GitArgs @('-C', $source, 'rev-parse', 'HEAD')
$sourceRefs = @(Invoke-Git -GitArgs @('-C', $source, 'for-each-ref', '--format=%(refname) %(objectname)'))
$branch = Invoke-Git -GitArgs @('-C', $source, 'branch', '--show-current')
if ($branch -ne 'main') { throw 'Check out main before preparing a release.' }
if (Invoke-Git -GitArgs @('-C', $source, 'status', '--porcelain')) {
    throw 'Commit all non-ignored changes first. Private ignored files are not copied.'
}
$excludes = @(Get-Content (Join-Path $PSScriptRoot 'public-release-excludes.txt') |
    Where-Object { $_.Trim().Length -gt 0 })
foreach ($path in $excludes) {
    if ($path -notmatch '^frontend/[A-Za-z0-9_./-]+$' -or $path.Contains('..')) {
        throw "Unsafe exclusion path: $path"
    }
}
$currentExcluded = @(Invoke-Git -GitArgs (@('-C', $source, 'ls-files', '--') + $excludes))
if ($currentExcluded.Count -gt 0) { throw 'Excluded assets are still tracked at HEAD.' }

$releaseRoot = [IO.Path]::GetFullPath((Join-Path $source 'release'))
$run = Join-Path $releaseRoot ('public-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
if (Test-Path -LiteralPath $run) { throw 'Release target already exists; nothing overwritten.' }
if ((Test-Path -LiteralPath $releaseRoot) -and
    ((Get-Item -LiteralPath $releaseRoot).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
    throw 'Release directory must not be a symlink or junction.'
}
New-Item -ItemType Directory -Path $run | Out-Null
$backup = Join-Path $run 'PRIVATE-full-history.bundle'
$stage = Join-Path $run 'PRIVATE-filter-work'
$public = Join-Path $run 'repository'
$archive = Join-Path $run 'xidao-public-source.zip'

Write-Host 'Backing up all source refs (PRIVATE: do not upload the bundle).'
Invoke-Git -GitArgs @('-C', $source, 'bundle', 'create', $backup, '--all') | Out-Null
Invoke-Git -GitArgs @('-C', $source, 'bundle', 'verify', $backup) | Out-Null

# --no-local avoids hardlinks, alternates, and any shared source object store.
# Only main and its ancestry are published; the bundle preserves ALL source refs.
Invoke-Git -GitArgs @('clone', '--no-local', '--single-branch', '--no-tags', '--branch', 'main', $source, $stage) | Out-Null
Invoke-Git -GitArgs @('-C', $stage, 'remote', 'remove', 'origin') | Out-Null
if ((Invoke-Git -GitArgs @('-C', $stage, 'rev-parse', '--show-toplevel')).Replace('/', '\') -ne $stage) {
    throw 'Filter target validation failed.'
}
$excludedBlobs = [Collections.Generic.HashSet[string]]::new()
$sourceCommits = @(Invoke-Git -GitArgs @('-C', $source, 'rev-list', '--all'))
foreach ($commit in $sourceCommits) {
    $rows = @(Invoke-Git -GitArgs (@('-C', $source, 'ls-tree', '-r', '--format=%(objectname)', $commit, '--') + $excludes))
    foreach ($oid in $rows) { [void]$excludedBlobs.Add($oid) }
}

Write-Host 'Filtering main history in the disposable PRIVATE copy only.'
# Git for Windows includes filter-branch. For this small history an index filter
# needs no downloaded tooling and never checks out or deletes source assets.
$indexFilter = 'git rm -r --cached --ignore-unmatch -- ' +
    (($excludes | ForEach-Object { "'$_'" }) -join ' ')
$previousWarning = $env:FILTER_BRANCH_SQUELCH_WARNING
try {
    $env:FILTER_BRANCH_SQUELCH_WARNING = '1'
    Invoke-Git -GitArgs @('-C', $stage, 'filter-branch', '--index-filter', $indexFilter, '--', '--all') | Out-Null
} finally {
    $env:FILTER_BRANCH_SQUELCH_WARNING = $previousWarning
}

# A second transport clone transfers only rewritten main ancestry, not the
# filter tool's refs/original, reflogs, or leftover private/unreachable objects.
Invoke-Git -GitArgs @('clone', '--no-local', '--single-branch', '--no-tags', '--branch', 'main', $stage, $public) | Out-Null
Invoke-Git -GitArgs @('-C', $public, 'remote', 'remove', 'origin') | Out-Null
$publicRefs = @(Invoke-Git -GitArgs @('-C', $public, 'for-each-ref', '--format=%(refname)'))
if ($publicRefs.Count -ne 1 -or $publicRefs[0] -ne 'refs/heads/main') {
    throw 'Unexpected refs in public copy.'
}
if (Test-Path -LiteralPath (Join-Path $public '.git/objects/info/alternates')) {
    throw 'Public copy must not share object storage.'
}
$remaining = @(Invoke-Git -GitArgs (@('-C', $public, 'log', '--all', '--format=%H', '--') + $excludes))
if ($remaining.Count -gt 0) { throw 'Excluded paths still occur in public history.' }
$allObjects = @(Invoke-Git -GitArgs @('-C', $public, 'cat-file', '--batch-all-objects', '--batch-check=%(objectname)'))
foreach ($oid in $allObjects) {
    if ($excludedBlobs.Contains($oid)) { throw "Private asset object still present: $oid" }
}
$sourceTree = Invoke-Git -GitArgs @('-C', $source, 'rev-parse', 'HEAD^{tree}')
$publicTree = Invoke-Git -GitArgs @('-C', $public, 'rev-parse', 'HEAD^{tree}')
if ($sourceTree -ne $publicTree) { throw 'Release source tree differs from committed source HEAD.' }
Invoke-Git -GitArgs @('-C', $public, 'fsck', '--full', '--strict') | Out-Null
if (Invoke-Git -GitArgs @('-C', $public, 'status', '--porcelain')) { throw 'Public worktree is not clean.' }
if ((Invoke-Git -GitArgs @('-C', $source, 'rev-parse', 'HEAD')) -ne $head -or
    (Compare-Object $sourceRefs @(Invoke-Git -GitArgs @('-C', $source, 'for-each-ref', '--format=%(refname) %(objectname)')))) {
    throw 'Source refs changed during release preparation; review before use.'
}

Invoke-Git -GitArgs @('-C', $public, 'archive', '--format=zip', '--prefix=xidao-public/', "--output=$archive", 'HEAD') | Out-Null
$report = [ordered]@{
    createdAt = (Get-Date).ToString('o')
    sourceHead = $head
    publicHead = (Invoke-Git -GitArgs @('-C', $public, 'rev-parse', 'HEAD'))
    identicalHeadTree = $sourceTree
    sourceRefsPreserved = $sourceRefs
    publishedRefs = $publicRefs
    sourceMainCommits = [int](Invoke-Git -GitArgs @('-C', $source, 'rev-list', '--count', 'main'))
    publicMainCommits = [int](Invoke-Git -GitArgs @('-C', $public, 'rev-list', '--count', 'main'))
    excludedPaths = $excludes
    excludedBlobCount = $excludedBlobs.Count
    excludedBlobsAbsentFromEntirePublicObjectStore = $true
    publicObjectCount = $allObjects.Count
    publicRepository = $public
    sourceArchive = $archive
    archiveSHA256 = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
    privateHistoryBackup = $backup
    backupSHA256 = (Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash
    warning = 'Upload only repository or xidao-public-source.zip. Never upload this parent directory, PRIVATE bundle, or PRIVATE-filter-work. No remote push performed. This is an asset-history audit, not a comprehensive secret scan.'
}
# Generated audit output, not a hand-edited source file.
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $run 'audit.json') -Encoding UTF8
Write-Host "Verified public repository: $public"
Write-Host "Source archive: $archive"
Write-Host 'Original repository refs and local files preserved. Nothing pushed.'

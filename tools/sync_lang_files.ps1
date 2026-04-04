param(
    [switch]$Verify,
    [string]$ReportPath
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$langDir = Join-Path $repoRoot 'src\main\resources\assets\adaptivemobai\lang'
$sourceLocale = 'en_us'

$expectedLocales = @(
    'af_za','ar_sa','ast_es','az_az','ba_ru','bar','be_by','bg_bg','br_fr','brb','bs_ba','ca_es','cs_cz','cy_gb','da_dk',
    'de_at','de_ch','de_de','el_gr','en_au','en_ca','en_gb','en_nz','en_pt','en_ud','en_us','enp','enws','eo_uy','es_ar',
    'es_cl','es_ec','es_es','es_mx','es_uy','es_ve','esan','et_ee','eu_es','fa_ir','fi_fi','fil_ph','fo_fo','fr_ca','fr_fr',
    'fra_de','fur_it','fy_nl','ga_ie','gd_gb','gl_es','haw_us','he_il','hi_in','hr_hr','hu_hu','hy_am','id_id','ig_ng',
    'io_en','is_is','isv','it_it','ja_jp','jbo_en','ka_ge','kk_kz','kn_in','ko_kr','ksh','kw_gb','la_la','lb_lu','li_li',
    'lmo','lo_la','lol_us','lt_lt','lv_lv','lzh','mk_mk','mn_mn','ms_my','mt_mt','nah','nds_de','nl_be','nl_nl','nn_no',
    'no_no','oc_fr','ovd','pl_pl','pt_br','pt_pt','qya_aa','ro_ro','rpr','ru_ru','ry_ua','sah_sah','se_no','sk_sk','sl_si',
    'so_so','sq_al','sr_cs','sr_sp','sv_se','sxu','szl','ta_in','th_th','tl_ph','tlh_aa','tok','tr_tr','tt_ru','uk_ua',
    'val_es','vec_it','vi_vn','yi_de','yo_ng','zh_cn','zh_hk','zh_tw','zlm_arab'
)

$translationExemptLocales = @(
    'en_au','en_ca','en_gb','en_nz','en_pt','en_ud','en_us','enp','enws','lol_us'
)

$localeDonorFamilies = @{
    'ast_es' = @('es_es','ca_es')
    'ba_ru' = @('ru_ru','tt_ru')
    'bar' = @('de_de','de_at')
    'br_fr' = @('fr_fr','oc_fr')
    'brb' = @('nl_nl','de_de')
    'eo_uy' = @('eo_uy','es_es')
    'esan' = @('es_es','es_mx')
    'fil_ph' = @('tl_ph','id_id')
    'fo_fo' = @('da_dk','nn_no')
    'fra_de' = @('de_de','fr_fr')
    'fur_it' = @('it_it','vec_it')
    'fy_nl' = @('nl_nl','de_de')
    'haw_us' = @('en_us','enp')
    'io_en' = @('eo_uy','en_us')
    'isv' = @('uk_ua','pl_pl','ru_ru')
    'jbo_en' = @('eo_uy','la_la')
    'ksh' = @('de_de','nl_nl')
    'kw_gb' = @('cy_gb','en_gb')
    'la_la' = @('it_it','fr_fr')
    'lb_lu' = @('de_de','fr_fr','nl_nl')
    'li_li' = @('nl_nl','de_de')
    'lmo' = @('it_it','vec_it')
    'lzh' = @('zh_tw','zh_cn')
    'nah' = @('es_es','es_mx')
    'nds_de' = @('de_de','nl_nl')
    'nn_no' = @('no_no','da_dk')
    'no_no' = @('nn_no','da_dk')
    'oc_fr' = @('ca_es','fr_fr','es_es')
    'ovd' = @('sv_se','no_no')
    'pt_br' = @('pt_pt','es_es')
    'pt_pt' = @('pt_br','es_es')
    'qya_aa' = @('la_la','eo_uy')
    'rpr' = @('ru_ru','uk_ua')
    'ry_ua' = @('uk_ua','ru_ru')
    'sah_sah' = @('ru_ru','tt_ru')
    'se_no' = @('no_no','sv_se')
    'sr_cs' = @('sr_sp','hr_hr')
    'sr_sp' = @('sr_cs','hr_hr')
    'sxu' = @('de_de','bar')
    'szl' = @('pl_pl','cs_cz')
    'tl_ph' = @('fil_ph','id_id')
    'tlh_aa' = @('la_la','eo_uy')
    'tok' = @('eo_uy','id_id','en_us')
    'tt_ru' = @('ru_ru','ba_ru')
    'val_es' = @('ca_es','es_es')
    'vec_it' = @('it_it','fur_it')
    'yi_de' = @('he_il','de_de','en_us')
    'zh_cn' = @('zh_tw','ja_jp','ko_kr')
    'zh_hk' = @('zh_tw','zh_cn')
    'zh_tw' = @('zh_hk','zh_cn')
    'zlm_arab' = @('ms_my','ar_sa')
}

function Read-LangObject {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }

    return Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Get-OrderedEntries {
    param($Object)

    return @($Object.PSObject.Properties | ForEach-Object {
        [PSCustomObject]@{
            Key = $_.Name
            Value = [string]$_.Value
        }
    })
}

function ConvertTo-OrderedMap {
    param([object[]]$Entries)

    $ordered = [ordered]@{}
    foreach ($entry in $Entries) {
        $ordered[$entry.Key] = $entry.Value
    }

    return $ordered
}

function ConvertTo-LangJson {
    param([object[]]$Entries)

    return (ConvertTo-OrderedMap -Entries $Entries | ConvertTo-Json -Depth 4) + [Environment]::NewLine
}

function Normalize-LineEndings {
    param([string]$Text)

    if ($null -eq $Text) {
        return $null
    }

    return (($Text -replace "`r`n", "`n") -replace "`r", "`n")
}

function Write-LangFile {
    param(
        [string]$Path,
        [object[]]$Entries
    )

    [System.IO.File]::WriteAllText($Path, (ConvertTo-LangJson -Entries $Entries), [System.Text.UTF8Encoding]::new($false))
}

function Get-PlaceholderCount {
    param([string]$Text)

    return ([regex]::Matches($Text, '%s')).Count
}

function Get-NormalizedTextLength {
    param([string]$Text)

    $normalized = [regex]::Replace($Text, '%s', '')
    $normalized = [regex]::Replace($normalized, '[^\p{L}\p{N}]', '')
    return $normalized.Length
}

function Get-TranslationValidationErrors {
    param(
        [string]$Locale,
        [object[]]$EnglishEntries,
        [object[]]$LocalizedEntries
    )

    $errors = New-Object System.Collections.Generic.List[string]
    if ($translationExemptLocales -contains $Locale) {
        return @($errors)
    }

    $collapsedEntries = 0
    $englishLeakEntries = 0
    $identicalEntries = 0
    $privateUsePattern = '[\uE000-\uF8FF]'
    $englishLeakPattern = '(?i)\b(open|close|save|cancel|choose|return|force|enable|disable|learned|default|none)\b'

    for ($index = 0; $index -lt $EnglishEntries.Count; $index++) {
        $key = $EnglishEntries[$index].Key
        $source = [string]$EnglishEntries[$index].Value
        $localized = [string]$LocalizedEntries[$index].Value

        if ($localized -match $privateUsePattern) {
            $errors.Add("$key contains leaked placeholder/separator characters.")
        }

        if ((Get-PlaceholderCount -Text $source) -ne (Get-PlaceholderCount -Text $localized)) {
            $errors.Add("$key does not preserve %s placeholders.")
        }

        if (-not [string]::IsNullOrWhiteSpace($source) -and [string]::IsNullOrWhiteSpace($localized)) {
            $errors.Add("$key translated to an empty value.")
        }

        $sourceLength = Get-NormalizedTextLength -Text $source
        $localizedLength = Get-NormalizedTextLength -Text $localized
        if ($sourceLength -ge 6 -and $localizedLength -le 1) {
            $collapsedEntries++
            $errors.Add("$key collapsed to a suspiciously short translation.")
        }
        elseif ($sourceLength -ge 12 -and $localizedLength -le 2) {
            $collapsedEntries++
            $errors.Add("$key is much shorter than the source and looks truncated.")
        }

        if ($sourceLength -ge 8 -and $localized.Trim() -ceq $source.Trim()) {
            $identicalEntries++
        }

        if ($localized -match $englishLeakPattern) {
            $englishLeakEntries++
        }
    }

    if ($collapsedEntries -gt 0) {
        $errors.Add("Locale '$Locale' contains $collapsedEntries suspiciously short translations.")
    }

    if ($identicalEntries -gt 0) {
        $errors.Add("Locale '$Locale' still contains $identicalEntries untranslated values copied from en_us.")
    }

    if ($englishLeakEntries -gt 0) {
        $errors.Add("Locale '$Locale' still contains $englishLeakEntries obvious English fallback phrases.")
    }

    return @($errors | Select-Object -Unique)
}

function Get-LocaleDonorFamilies {
    param([string]$Locale)

    if ($localeDonorFamilies.ContainsKey($Locale)) {
        return @($localeDonorFamilies[$Locale])
    }

    if ($translationExemptLocales -contains $Locale) {
        return @('en_us')
    }

    if ($Locale -match '_') {
        $languageCode = $Locale.Split('_')[0]
        $sameFamily = @($expectedLocales | Where-Object { $_ -ne $Locale -and $_.StartsWith($languageCode + '_') })
        if ($sameFamily.Count -gt 0) {
            return $sameFamily
        }
    }

    return @('en_us')
}

function Format-FailReport {
    param(
        [object[]]$Failures,
        [string]$RepoLabel
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Locale Gate Fail Report ($RepoLabel)")
    $lines.Add('')
    $lines.Add("Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')")
    $lines.Add('')
    $lines.Add("Failing locales: $($Failures.Count)")
    $lines.Add('')

    foreach ($failure in $Failures | Sort-Object Locale) {
        $lines.Add("## $($failure.Locale)")
        $lines.Add('')
        $lines.Add("Suggested donor families: $($failure.DonorFamilies -join ', ')")
        $lines.Add('')
        foreach ($issue in $failure.Issues) {
            $lines.Add("- $issue")
        }
        $lines.Add('')
    }

    return ($lines -join [Environment]::NewLine) + [Environment]::NewLine
}

function Get-LocaleFailures {
    $failures = New-Object System.Collections.Generic.List[object]

    $actualFiles = @(Get-ChildItem -LiteralPath $langDir -Filter '*.json' | Select-Object -ExpandProperty BaseName | Sort-Object)
    $expectedFiles = @($expectedLocales | Sort-Object)
    $missing = @($expectedFiles | Where-Object { $_ -notin $actualFiles })
    $extra = @($actualFiles | Where-Object { $_ -notin $expectedFiles })

    if ($missing.Count -gt 0 -or $extra.Count -gt 0) {
        $issues = New-Object System.Collections.Generic.List[string]
        if ($missing.Count -gt 0) {
            $issues.Add("Missing locale files: $($missing -join ', ')")
        }
        if ($extra.Count -gt 0) {
            $issues.Add("Unexpected locale files: $($extra -join ', ')")
        }

        $failures.Add([PSCustomObject]@{
            Locale = 'locale-set'
            DonorFamilies = @('en_us')
            Issues = @($issues)
        })
    }

    $englishPath = Join-Path $langDir ($sourceLocale + '.json')
    $englishEntries = Get-OrderedEntries -Object (Read-LangObject -Path $englishPath)
    $englishKeys = @($englishEntries | ForEach-Object { $_.Key })

    foreach ($locale in $expectedLocales) {
        $localePath = Join-Path $langDir ($locale + '.json')
        $issues = New-Object System.Collections.Generic.List[string]

        if (-not (Test-Path -LiteralPath $localePath)) {
            $issues.Add("Missing locale file '$locale.json'.")
        }
        else {
            $existingObject = Read-LangObject -Path $localePath
            $localeEntries = Get-OrderedEntries -Object $existingObject
            $localeKeys = @($localeEntries | ForEach-Object { $_.Key })

            if (@(Compare-Object -ReferenceObject $englishKeys -DifferenceObject $localeKeys).Count -gt 0) {
                $issues.Add("Key set does not match en_us.")
            }

            $expectedEntries = Get-SyncedEntries -EnglishEntries $englishEntries -ExistingObject $existingObject -Locale $locale
            $expectedJson = Normalize-LineEndings -Text (ConvertTo-LangJson -Entries $expectedEntries)
            $actualJson = Normalize-LineEndings -Text (Get-Content -LiteralPath $localePath -Raw -Encoding UTF8)
            if ($actualJson -ne $expectedJson) {
                $issues.Add("File would be rewritten by tools/sync_lang_files.ps1.")
            }

            $validationErrors = Get-TranslationValidationErrors -Locale $locale -EnglishEntries $englishEntries -LocalizedEntries $localeEntries
            foreach ($validationError in $validationErrors) {
                $issues.Add($validationError)
            }
        }

        if ($issues.Count -gt 0) {
            $failures.Add([PSCustomObject]@{
                Locale = $locale
                DonorFamilies = Get-LocaleDonorFamilies -Locale $locale
                Issues = @($issues | Select-Object -Unique)
            })
        }
    }

    return @($failures.ToArray())
}

function Get-SyncedEntries {
    param(
        [object[]]$EnglishEntries,
        [object]$ExistingObject,
        [string]$Locale
    )

    $existingMap = @{}
    if ($null -ne $ExistingObject) {
        foreach ($property in $ExistingObject.PSObject.Properties) {
            $existingMap[$property.Name] = [string]$property.Value
        }
    }

    $syncedEntries = foreach ($entry in $EnglishEntries) {
        $value = if ($existingMap.ContainsKey($entry.Key)) { $existingMap[$entry.Key] } else { $entry.Value }

        if (($translationExemptLocales -contains $Locale) -and $locale -ne $sourceLocale) {
            $value = $entry.Value
        }

        [PSCustomObject]@{
            Key = $entry.Key
            Value = [string]$value
        }
    }

    return @($syncedEntries)
}

function Assert-ExpectedLocaleSet {
    $actualFiles = @(Get-ChildItem -LiteralPath $langDir -Filter '*.json' | Select-Object -ExpandProperty BaseName | Sort-Object)
    $expectedFiles = @($expectedLocales | Sort-Object)

    $missing = @($expectedFiles | Where-Object { $_ -notin $actualFiles })
    $extra = @($actualFiles | Where-Object { $_ -notin $expectedFiles })
    if ($missing.Count -gt 0 -or $extra.Count -gt 0) {
        throw "Locale set mismatch. Missing: $($missing -join ', '). Extra: $($extra -join ', ')."
    }
}

function Sync-LangFiles {
    $englishPath = Join-Path $langDir ($sourceLocale + '.json')
    $englishEntries = Get-OrderedEntries -Object (Read-LangObject -Path $englishPath)

    foreach ($locale in $expectedLocales) {
        $localePath = Join-Path $langDir ($locale + '.json')
        $existingObject = Read-LangObject -Path $localePath
        $syncedEntries = Get-SyncedEntries -EnglishEntries $englishEntries -ExistingObject $existingObject -Locale $locale
        Write-LangFile -Path $localePath -Entries $syncedEntries
    }

    Write-Host 'Forge language files synchronized to en_us structure.'
}

function Test-LangFiles {
    $failures = Get-LocaleFailures
    if ($ReportPath) {
        $resolvedReportPath = if ([System.IO.Path]::IsPathRooted($ReportPath)) { $ReportPath } else { Join-Path $repoRoot $ReportPath }
        $reportDirectory = Split-Path -Parent $resolvedReportPath
        if ($reportDirectory -and -not (Test-Path -LiteralPath $reportDirectory)) {
            New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
        }

        $reportContent = Format-FailReport -Failures $failures -RepoLabel 'Forge'
        [System.IO.File]::WriteAllText($resolvedReportPath, $reportContent, [System.Text.UTF8Encoding]::new($false))
        Write-Host "Wrote locale fail report to $resolvedReportPath"
    }

    if ($failures.Count -gt 0) {
        $summary = @($failures | ForEach-Object { "$($_.Locale): $($_.Issues.Count) issue(s)" }) -join '; '
        throw "Language gate failed for $($failures.Count) locale target(s): $summary"
    }

    Write-Host 'Forge language file verification passed.'
}

$isCi = ($env:GITHUB_ACTIONS -eq 'true') -or ($env:CI -eq 'true')
if ($Verify -or $isCi) {
    Test-LangFiles
}
else {
    Sync-LangFiles
}
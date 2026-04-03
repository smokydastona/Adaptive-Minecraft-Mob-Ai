param()

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

$ignoredLocales = @(
    'en_au','en_ca','en_gb','en_nz','en_pt','en_ud','en_us','enp','enws','lol_us'
)

function Read-LangObject {
    param([string]$Path)

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
    if ($ignoredLocales -contains $Locale) {
        return @($errors)
    }

    $collapsedEntries = 0
    $englishLeakEntries = 0
    $identicalEntries = 0
    $privateUsePattern = '[\uE000-\uF8FF]'
    $englishLeakPattern = '(?i)\b(open|close|save|cancel|choose|return|force|enable|disable|learned)\b'

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
        $errors.Add("Locale '$Locale' still contains $englishLeakEntries obvious English UI phrases.")
    }

    return @($errors | Select-Object -Unique)
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

function Test-LangFiles {
    Assert-ExpectedLocaleSet

    $englishPath = Join-Path $langDir ($sourceLocale + '.json')
    $englishEntries = Get-OrderedEntries -Object (Read-LangObject -Path $englishPath)
    $englishKeys = @($englishEntries | ForEach-Object { $_.Key })

    foreach ($locale in $expectedLocales) {
        $localePath = Join-Path $langDir ($locale + '.json')
        $localeEntries = Get-OrderedEntries -Object (Read-LangObject -Path $localePath)
        $localeKeys = @($localeEntries | ForEach-Object { $_.Key })

        if (@(Compare-Object -ReferenceObject $englishKeys -DifferenceObject $localeKeys).Count -gt 0) {
            throw "Locale '$locale' does not match the en_us key set."
        }

        $validationErrors = Get-TranslationValidationErrors -Locale $locale -EnglishEntries $englishEntries -LocalizedEntries $localeEntries
        if ($validationErrors.Count -gt 0) {
            throw "Locale '$locale' failed quality checks: $($validationErrors -join ' ')"
        }
    }

    Write-Host 'Forge language file verification passed.'
}

Test-LangFiles
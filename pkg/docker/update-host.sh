#!/usr/bin/env bash
set -euo pipefail

# Run on the Docker host with the original Compose file and its /app/data bind mount.
compose_file=$(realpath -- "${1:?Usage: update-host.sh COMPOSE_FILE DATA_DIRECTORY HOST_STATE_DIRECTORY [MIRRORS_FILE]}")
data=$(realpath -- "${2:?Usage: update-host.sh COMPOSE_FILE DATA_DIRECTORY HOST_STATE_DIRECTORY [MIRRORS_FILE]}")
state="$data/docker-update"
host_state=$(realpath -m -- "${3:?Provide a host-only state directory outside the data mount}")
mirrors_file=''
if [[ -n ${4:-} ]]; then
    mirrors_file=$(realpath -- "$4")
fi
for host_path in "$host_state" "$compose_file" "$mirrors_file"; do
    case "$host_path/" in
        "$data/"*) echo "Host configuration must be outside the container data mount" >&2; exit 2 ;;
    esac
done
umask 077
mkdir -p -- "$host_state"
override="$host_state/compose.update.yaml"
mkdir -p -- "$state"
[[ ! -L "$state" ]] || exit 2
cd -P -- "$state"
state=.
exec 9>"$host_state/host.lock"
flock -n 9
compose=(docker compose -f "$compose_file")
if [[ -s "$override" ]]; then
    compose+=(-f "$override")
fi

apply_update() {
    local target current live version candidate repository digest original_image installed image_id new_container
    local -a request
    [[ -f "$state/working" && ! -L "$state/working" ]] || return 1
    mapfile -t request < "$state/working"
    [[ ${#request[@]} == 4 ]] || return 1
    target=${request[0]}
    current=${request[1]}
    [[ $target =~ ^v?[0-9]+\.[0-9]+\.[0-9]+$ && $current =~ ^v?[0-9]+\.[0-9]+\.[0-9]+$ ]] || return 1
    version=${target#v}
    current=${current#v}
    [[ $version != "$current" && $(printf '%s\n' "$version" "$current" | sort -V | tail -1) == "$version" ]] || return 1
    live=$("${compose[@]}" ps -q peerbanhelper) || return 1
    [[ -n $live ]] || return 1
    original_image=$(docker inspect -f '{{.Image}}' "$live") || return 1
    installed=$(docker image inspect -f '{{index .Config.Labels "org.opencontainers.image.version"}}' "$original_image") || return 1
    installed=${installed%-jvm-universal}
    [[ ${installed#v} == "$current" ]] || return 1
    local image=''
    local -a repositories=('ghostchu/peerbanhelper hub')
    if [[ -n $mirrors_file ]]; then
        mapfile -t mirrors < "$mirrors_file" || return 1
        repositories+=("${mirrors[@]}")
    else
        repositories+=('registry.cn-hangzhou.aliyuncs.com/ghostchu/peerbanhelper mirror')
    fi
    local entry kind extra
    local -A attempted=()
    for entry in "${repositories[@]}"; do
        read -r repository kind extra <<< "${entry%$'\r'}"
        [[ -n $repository && $repository != \#* ]] || continue
        [[ -z $extra && $repository =~ ^[a-z0-9][a-z0-9.:/_-]+$ ]] || return 1
        case "$kind" in
            hub) digest=${request[2]} ;;
            mirror) digest=${request[3]} ;;
            *) return 1 ;;
        esac
        [[ $digest =~ ^sha256:[a-f0-9]{64}$ ]] || return 1
        candidate="$repository@$digest"
        [[ ! -v 'attempted[$candidate]' ]] || continue
        attempted[$candidate]=1
        if docker pull "$candidate"; then
            installed=$(docker image inspect -f '{{index .Config.Labels "org.opencontainers.image.version"}}' "$candidate") || continue
            installed=${installed%-jvm-universal}
            if [[ ${installed#v} == "$version" ]]; then
                image=$candidate
                break
            fi
        fi
    done
    [[ -n $image && -f "$state/working" ]] || return 1
    [[ $("${compose[@]}" ps -q peerbanhelper) == "$live" ]] || return 1
    local temporary
    temporary=$(mktemp "$host_state/compose.XXXXXX.yaml") || return 1
    printf 'services:\n  peerbanhelper:\n    image: %s\n' "$image" > "$temporary"
    local -a next=(docker compose -f "$compose_file" -f "$temporary")
    image_id=$(docker image inspect -f '{{.Id}}' "$image") || return 1
    if ! "${next[@]}" up -d --no-deps --wait --wait-timeout 90 peerbanhelper ||
        ! new_container=$("${next[@]}" ps -q peerbanhelper) ||
        [[ $(docker inspect -f '{{.Image}}' "$new_container") != "$image_id" ]]; then
        rm -f -- "$temporary"
        "${compose[@]}" up -d --no-deps peerbanhelper || true
        return 1
    fi
    mv -f -- "$temporary" "$override" || return 1
    compose=(docker compose -f "$compose_file" -f "$override")
}

trap 'rm -f -- "$state/enabled"' EXIT
trap 'exit 0' INT TERM
# An interrupted host update must be checked again against the running image.
if [[ -f "$state/working" ]]; then
    rm -f -- "$state/working"
    rm -f -- "$state/failed"
    touch "$state/failed"
fi
while :; do
    rm -f -- "$state/enabled"
    touch "$state/enabled"
    if mv -- "$state/request" "$state/working" 2>/dev/null; then
        if ! apply_update; then
            rm -f -- "$state/failed"
            touch "$state/failed"
        fi
        rm -f -- "$state/working"
    fi
    sleep 2
done

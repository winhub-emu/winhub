/*
 * Link probe for libhermes_guest.a: references every function of the C ABI so the whole
 * library is pulled into a shared object linked the way the libfakeinput.so builds link it.
 * ci/build.sh then checks that object's dynamic dependencies and exports. Valid C and C++.
 */
#include "hermes.h"

#include <fcntl.h>
#include <string.h>

#ifdef __cplusplus
extern "C" {
#endif

__attribute__((visibility("default"))) long hermes_linkprobe_entry(int slot);

/* struct hermes_identity must match the library's layout (core/src/guest.rs IdentityView). */
typedef char hermes_identity_size_check[(sizeof(struct hermes_identity) == 92) ? 1 : -1];
typedef char hermes_identity_key_check[(offsetof(struct hermes_identity, key) == 12) ? 1 : -1];
typedef char hermes_identity_name_check[(offsetof(struct hermes_identity, name) == 28) ? 1 : -1];
/* struct hermes_ff_effect must match the library's (core/src/ff.rs FfEffect). */
typedef char hermes_ff_effect_size_check[(sizeof(struct hermes_ff_effect) == 44) ? 1 : -1];
typedef char hermes_ff_effect_u_check[(offsetof(struct hermes_ff_effect, u) == 12) ? 1 : -1];
typedef char hermes_ff_effect_delay_check[(offsetof(struct hermes_ff_effect, replay_delay) == 8) ? 1 : -1];

static void probe_log(int level, const char *line)
{
    (void)level;
    (void)line;
}

long hermes_linkprobe_entry(int slot)
{
    hermes_handle h = 0;
    struct hermes_state st;
    struct hermes_stats gs;
    struct hermes_slot_stats ss;
    struct hermes_identity id;
    struct hermes_ff_effect fx;
    uint8_t ffbits[16];
    unsigned char buf[HERMES_EVENT_SIZE * 4];
    long acc = 0;
    int fd;

    hermes_set_logger(probe_log);
    acc += hermes_init();
    fd = hermes_attach(slot, O_RDONLY | O_NONBLOCK | O_CLOEXEC, &h);
    if (fd >= 0) {
        acc += hermes_fd(h) + hermes_slot(h) + hermes_is_removed(h) + hermes_has_pending(h);
        acc += hermes_poll_revents(h, 1);
        acc += (long)hermes_read(h, buf, sizeof buf, 1);
        acc += hermes_current_state(h, &st);
        acc += hermes_identity(h, &id, sizeof id);
        acc += hermes_ff_bits(h, ffbits, sizeof ffbits);
        memset(&fx, 0, sizeof fx);
        fx.type = HERMES_FF_RUMBLE;
        fx.id = -1;
        fx.u.rumble.strong_magnitude = 0x8000;
        acc += hermes_ff_upload(h, &fx, sizeof fx);
        acc += hermes_ff_play(h, fx.id, 1) + hermes_ff_set_gain(h, 0xffff) + hermes_ff_set_autocenter(h, 0);
        acc += hermes_ff_erase(h, fx.id);
        acc += hermes_detach(h);
    }
    acc += hermes_get_stats(&gs, sizeof gs);
    acc += hermes_slot_stats(slot, &ss, sizeof ss);
    acc += hermes_slot_identity(slot, &id, sizeof id);
    acc += (long)hermes_api_version();
    acc += (long)hermes_axis_hz() + hermes_axis_auto();
    hermes_set_logger(0);
    return acc;
}

#ifdef __cplusplus
}
#endif

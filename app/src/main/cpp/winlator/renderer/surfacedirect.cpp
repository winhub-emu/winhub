#include <sys/socket.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/epoll.h>
#include <sys/un.h>
#include <string.h>
#include <thread>
#include <mutex>
#include <array>
#include <cerrno>
#include <unordered_map>
#include <sys/eventfd.h>

#include <android/api-level.h>
#include <android/log.h>
#include <android/hardware_buffer.h>
#include <android/surface_control.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include "surfacedirect.hpp"

#define LOG_TAG "SurfaceDirect"
#define printf(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

using PFNASURFACETRANSACTIONSETPOSITION = void (*)(ASurfaceTransaction*, ASurfaceControl*, int32_t, int32_t);
using PFNASURFACETRANSACTIONSETBUFFER = void (*)(ASurfaceTransaction*, ASurfaceControl*, AHardwareBuffer*, int);
using PFNASURFACETRANSACTIONSETGEOMETRY = void (*)(ASurfaceTransaction*, ASurfaceControl*, const ARect&, const ARect&, int32_t);
using PFNASURFACETRANSACTIONSETZORDER = void (*)(ASurfaceTransaction*, ASurfaceControl*, int32_t);
using PFNASURFACETRANSACTIONSETVISIBILITY = void (*)(ASurfaceTransaction*, ASurfaceControl*, enum ASurfaceTransactionVisibility);
using PFNASURFACETRANSACTIONSETBUFFERTRANSPARENCY = void (*)(ASurfaceTransaction*, ASurfaceControl*, enum ASurfaceTransactionTransparency);
using PFNASURFACETRANSACTIONSETBUFFERALPHA = void (*)(ASurfaceTransaction*, ASurfaceControl*, float);
using PFNASURFACETRANSACTIONSTATSGETPRESENTFENCEFD = int (*)(ASurfaceTransactionStats*);
using PFNASURFACETRANSACTIONREPARENT = void (*)(ASurfaceTransaction*, ASurfaceControl*, ASurfaceControl*);
using PFNASURFACETRANSACTIONSETONCOMPLETE = void (*)(ASurfaceTransaction*, void*, ASurfaceTransaction_OnComplete);
using PFNASURFACETRANSACTIONSETONCOMMIT = void (*)(ASurfaceTransaction*, void*, ASurfaceTransaction_OnCommit);
using PFNASURFACETRANSACTIONSETENABLEBACKPRESSURE = void (*)(ASurfaceTransaction*, ASurfaceControl*, bool);
using PFNASURFACETRANSACTIONSETFRAMETIMELINE = void (*)(ASurfaceTransaction*, AVsyncId);
using PFNASURFACETRANSACTIONCREATE = ASurfaceTransaction* (*)();
using PFNASURFACETRANSACTIONDELETE = void (*)(ASurfaceTransaction*);
using PFNASURFACETRANSACTIONAPPLY = void (*)(ASurfaceTransaction*);

using PFNASURFACECONTROLACQUIRE = void (*)(ASurfaceControl*);
using PFNASURFACECONTROLRELEASE = void (*)(ASurfaceControl*);
using PFNASURFACECONTROLCREATE = ASurfaceControl* (*)(ASurfaceControl*, const char*);
using PFNASURFACECONTROLCREATEFROMWINDOW = ASurfaceControl* (*)(ANativeWindow*, const char*);

using PFNACHOREOGRAPHERGETINSTANCE = AChoreographer* (*)();
using PFNACHOREOGRAPHERPOSTFRAMECALLBACK64 = void (*)(AChoreographer*, AChoreographer_frameCallback64, void*);
using PFNACHOREOGRAPHERPOSTVSYNCCALLBACK = void (*)(AChoreographer*, AChoreographer_vsyncCallback, void*);
using PFNACHOREOGRAPHERGETFRAMETIMELINEVSYNCID = AVsyncId (*)(const AChoreographerFrameCallbackData*, size_t);
using PFNACHOREOGRAPHERGETPREFERREDTIMELINEINDEX = size_t (*)(const AChoreographerFrameCallbackData*);


using PFNAPERFORMANCEHINTGETMANAGER = APerformanceHintManager* (*)();
using PFNAPERFORMANCEHINTCREATESESSION = APerformanceHintSession* (*)(APerformanceHintManager*, const int32_t*, size_t, int64_t);
using PFNAPERFORMANCEHINTREPORTACTUALWORKDURATION = int (*)(APerformanceHintSession*, int64_t);
using PFNAPERFORMANCEHINTUPDATETARGETWORKDURATION = int (*)(APerformanceHintSession*, int64_t);
using PFNAPERFORMANCEHINTCLOSESESSION = void (*)(APerformanceHintSession*);

static PFNASURFACETRANSACTIONSETPOSITION pfnASurfaceTransactionSetPosition = nullptr;
static PFNASURFACETRANSACTIONSETBUFFER pfnASurfaceTransactionSetBuffer = nullptr;
static PFNASURFACETRANSACTIONSETGEOMETRY pfnASurfaceTransactionSetGeometry = nullptr;
static PFNASURFACETRANSACTIONSETZORDER pfnASurfaceTransactionSetZOrder = nullptr;
static PFNASURFACETRANSACTIONSETVISIBILITY pfnASurfaceTransactionSetVisibility = nullptr;
static PFNASURFACETRANSACTIONSETBUFFERALPHA pfnASurfaceTransactionSetBufferAlpha = nullptr;
static PFNASURFACETRANSACTIONSETBUFFERTRANSPARENCY pfnASurfaceTransactionSetBufferTransparency = nullptr;
static PFNASURFACETRANSACTIONREPARENT pfnASurfaceTransactionReparent = nullptr;
static PFNASURFACETRANSACTIONSETONCOMPLETE pfnASurfaceTransactionSetOnComplete = nullptr;
static PFNASURFACETRANSACTIONSETONCOMMIT pfnASurfaceTransactionSetOnCommit = nullptr;
static PFNASURFACETRANSACTIONSETENABLEBACKPRESSURE pfnASurfaceTransactionSetEnableBackPressure = nullptr;
static PFNASURFACETRANSACTIONSTATSGETPRESENTFENCEFD pfnASurfaceTransactionStatsGetPresentFenceFd = nullptr;
static PFNASURFACETRANSACTIONSETFRAMETIMELINE pfnASurfaceTransactionSetFrameTimeline = nullptr;
static PFNASURFACETRANSACTIONCREATE pfnASurfaceTransactionCreate = nullptr;
static PFNASURFACETRANSACTIONDELETE pfnASurfaceTransactionDelete = nullptr;
static PFNASURFACETRANSACTIONAPPLY pfnASurfaceTransactionApply = nullptr;

static PFNASURFACECONTROLACQUIRE pfnASurfaceControlAcquire = nullptr;
static PFNASURFACECONTROLRELEASE pfnASurfaceControlRelease = nullptr;
static PFNASURFACECONTROLCREATE pfnASurfaceControlCreate = nullptr;
static PFNASURFACECONTROLCREATEFROMWINDOW pfnASurfaceControlCreateFromWindow = nullptr;

static PFNACHOREOGRAPHERGETINSTANCE pfnAChoreographerGetInstance = nullptr;
static PFNACHOREOGRAPHERPOSTFRAMECALLBACK64 pfnAChoreographerPostFrameCallback64 = nullptr;
static PFNACHOREOGRAPHERPOSTVSYNCCALLBACK pfnAChoreographerPostVsyncCallback = nullptr;
static PFNACHOREOGRAPHERGETFRAMETIMELINEVSYNCID pfnAChoreographerGetFrameTimelineVsyncId = nullptr;
static PFNACHOREOGRAPHERGETPREFERREDTIMELINEINDEX pfnAChoreographerGetPreferredTimelineIndex = nullptr;

static PFNAPERFORMANCEHINTGETMANAGER pfnAPerformanceHintGetManager = nullptr;
static PFNAPERFORMANCEHINTCREATESESSION pfnAPerformanceHintCreateSession = nullptr;
static PFNAPERFORMANCEHINTREPORTACTUALWORKDURATION pfnAPerformanceHintReportActualWorkDuration = nullptr;
static PFNAPERFORMANCEHINTUPDATETARGETWORKDURATION pfnAPerformanceHintUpdateTargetWorkDuration = nullptr;
static PFNAPERFORMANCEHINTCLOSESESSION pfnAPerformanceHintCloseSession = nullptr;

void SurfaceDirect::onFrameCallback64(int64_t frameTimeNanos, void* data) {
    auto *self = reinterpret_cast<SurfaceDirect *>(data);
    if (self->stopped) return;
   
    if (self->cursorUpdate && self->cursorManager->control && !self->paused) {
        self->eventLock.notify();
    }
    
    {
        auto lock = self->presentLock.lock();
        if (!self->presentRequests.empty() && self->presentRR) {
            self->requestUpdate = true;
            self->presentLock.notify();
        }
    }
    
    pfnAChoreographerPostFrameCallback64(self->choreographer, SurfaceDirect::onFrameCallback64, self);
}

void SurfaceDirect::onVsyncCallback(const AChoreographerFrameCallbackData* callbackData, void* data) {
    auto *self = reinterpret_cast<SurfaceDirect *>(data);
    if (self->stopped) return;
   
    if (self->cursorUpdate && self->cursorManager->control && !self->paused) {
        self->eventLock.notify();
    }
    
    size_t index = pfnAChoreographerGetPreferredTimelineIndex(callbackData);
    AVsyncId id = pfnAChoreographerGetFrameTimelineVsyncId(callbackData, index);
    
    {
        auto lock = self->presentLock.lock();
        self->vsyncId = id;
        if (!self->presentRequests.empty() && self->presentRR) {
            self->requestUpdate = true;
            self->presentLock.notify();
        }
    }
    
    pfnAChoreographerPostVsyncCallback(self->choreographer, SurfaceDirect::onVsyncCallback, self);
}

static void sendFD(int& socket, int fd) {
    std::vector<char> control_buffer(CMSG_SPACE(sizeof(int)));

    char dummy = 0;
    struct iovec iov{};
    iov.iov_len = 1;
    iov.iov_base = &dummy;

    struct msghdr msg{};
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    msg.msg_control = control_buffer.data();
    msg.msg_controllen = control_buffer.size();
                                                                                                             struct cmsghdr* cmsg = CMSG_FIRSTHDR(&msg);
    cmsg->cmsg_level = SOL_SOCKET;
    cmsg->cmsg_type = SCM_RIGHTS;
    cmsg->cmsg_len = CMSG_LEN(sizeof(int));
    
    *reinterpret_cast<int*>(CMSG_DATA(cmsg)) = fd;

    sendmsg(socket, &msg, 0);
}

static int readFD(int& socket) {
    std::vector<char> msg_contents(1);
    struct iovec iov{};
    iov.iov_base = msg_contents.data();
    iov.iov_len = msg_contents.size();
            
    std::vector<char> control_buf(CMSG_SPACE(sizeof(int)));
    struct msghdr msg{};
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    msg.msg_control = control_buf.data();
    msg.msg_controllen = control_buf.size();
            
    if (recvmsg(socket, &msg, MSG_WAITALL) <= 0)
        return -1;
            
    struct cmsghdr* cmsg = CMSG_FIRSTHDR(&msg);
    if (cmsg == nullptr || cmsg->cmsg_level != SOL_SOCKET || cmsg->cmsg_type != SCM_RIGHTS)
        return -1;
    int fd = *reinterpret_cast<int*>(CMSG_DATA(cmsg));
            
    return fd;
}


void SurfaceDirect::networkThreadLoop() {
    static constexpr int ADD_CLIENT_SWAPCHAIN = 1;
    static constexpr int PRESENT_IMAGE = 2;
    static constexpr int DESTROY_CLIENT_SWAPCHAIN = 3;
    static constexpr uint32_t MAX_SWAPCHAIN_IMAGES = 32;
    
    std::array<struct epoll_event, 4> events;
    std::unordered_map<uint8_t, std::unique_ptr<SurfaceDirectSwapchain>> clientSwapchains;
    std::unordered_set<int> clients;
    const int wakeFd = stopEventFd;
    
    int server_fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (server_fd < 0) {
        printf("Failed to create native rendering socket");
        return;
    }
                
    struct sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    memcpy(addr.sun_path + 1, "surfacedirect", strlen("surfacedirect"));
    addr.sun_path[0] = '\0';
    socklen_t len = offsetof(struct sockaddr_un, sun_path) + 1 + strlen("surfacedirect");
    if (bind(server_fd, (struct sockaddr *)&addr, len) < 0) {
        printf("Failed to bind native rendering socket");
        close(server_fd);
        return;
    }
               
    if (listen(server_fd, 1) < 0) {
        printf("Failed to listen to native rendering socket");
        close(server_fd);
        return;
    }
                
    int efd = epoll_create1(EPOLL_CLOEXEC);
    struct epoll_event event{};
    event.data.fd = server_fd;
    event.events = EPOLLIN;
    epoll_ctl(efd, EPOLL_CTL_ADD, server_fd, &event);
    
    // stop() writes here so the blocking wait below returns and the thread can be joined.
    event.data.fd = wakeFd;
    epoll_ctl(efd, EPOLL_CTL_ADD, wakeFd, &event);

    JNIEnv *env = cache->getEnv();
    
    auto dropClient = [&](int fd) {
        epoll_ctl(efd, EPOLL_CTL_DEL, fd, nullptr);
        close(fd);
        clients.erase(fd);
        for (auto &entry : clientSwapchains) {
            auto window = windowManager->getWindow(entry.second->windowId);
            if (window) window->externalContent = nullptr;
        }
        clientSwapchains.clear();
    };
           
    while (!stopped) {
        int n = epoll_wait(efd, events.data(), events.size(), -1);
        if (n < 0) {
            if (errno == EINTR) continue;
            break;
        }
        
        for (int i = 0; i < n && !stopped; i++) {
            int fd = events[i].data.fd;
            
            if (fd == wakeFd)
                continue;
            
            if (fd == server_fd) {
                if (events[i].events & EPOLLIN) {
                    printf("Received new client connection");
                    int client_fd = accept(server_fd, nullptr, nullptr);
                    if (client_fd < 0) continue;
                    
                    struct epoll_event clientEvent{};
                    clientEvent.data.fd = client_fd;
                    clientEvent.events = EPOLLIN;
                    epoll_ctl(efd, EPOLL_CTL_ADD, client_fd, &clientEvent);
                    clients.insert(client_fd);
                }
                continue;
            } 
            
            if (events[i].events & (EPOLLERR | EPOLLHUP)) {
                printf("Client has disconnected");
                dropClient(fd);
                continue;
            }
            
            if (!(events[i].events & EPOLLIN))
                continue;
            
            int request_code;
            if (read(fd, &request_code, 4) != 4) {
                dropClient(fd);
                continue;
            }
            
            switch (request_code) {
                case ADD_CLIENT_SWAPCHAIN:
                {
                    uint8_t id;
                    uint32_t imageCount;
                    uint32_t windowId;
                    
                    if (recv(fd, &id, 1, MSG_WAITALL) != 1 ||
                        recv(fd, &imageCount, 4, MSG_WAITALL) != 4 ||
                        recv(fd, &windowId, 4, MSG_WAITALL) != 4 ||
                        imageCount == 0 || imageCount > MAX_SWAPCHAIN_IMAGES) {
                        dropClient(fd);
                        continue;
                    }
                    
                    printf("Received new swapchain from client, id %d images %d", id, imageCount);
                    
                    // The buffers are always consumed, even for a window that is gone, or the stream falls out of step.
                    std::vector<AHardwareBuffer *> buffers;
                    bool failed = false;
                    for (uint32_t j = 0; j < imageCount; j++) {
                        AHardwareBuffer *buffer = nullptr;
                        if (AHardwareBuffer_recvHandleFromUnixSocket(fd, &buffer) != 0 || buffer == nullptr) {
                            failed = true;
                            break;
                        }
                        buffers.push_back(buffer);
                    }
                    
                    auto window = windowManager->getWindow(windowId);
                    if (failed || !window) {
                        for (auto buffer : buffers) AHardwareBuffer_release(buffer);
                        if (failed) dropClient(fd);
                        continue;
                    }
                    
                    auto swapchain = std::make_unique<SurfaceDirectSwapchain>();
                    swapchain->id = id;
                    swapchain->windowId = window->id;
                    
                    for (auto buffer : buffers) {
                        auto drawable = std::make_shared<Drawable>();
                        drawable->id = -1;
                        drawable->width = window->width;
                        drawable->height = window->height;
                        drawable->data = nullptr;
                        drawable->adoptAhb(buffer);
                        drawable->isDirectContent = false;
                        drawable->isSurfaceDirect = true;
                        drawable->drawableObj = nullptr;
                        drawable->sync_fence = -1;
                        swapchain->images.push_back(std::move(drawable));
                    }
                     
                    if (window->parent) window->parent->surface = window;
                    clientSwapchains[id] = std::move(swapchain);
                    break;
                }    
                case PRESENT_IMAGE:
                {
                    uint8_t id;
                    int index;
                    uint64_t present_id;
                            
                    if (recv(fd, &id, 1, MSG_WAITALL) != 1 || recv(fd, &index, 4, MSG_WAITALL) != 4) {
                        dropClient(fd);
                        continue;
                    }
                    
                    int fence = readFD(fd);
                    
                    if (recv(fd, &present_id, 8, MSG_WAITALL) != 8) {
                        if (fence >= 0) close(fence);
                        dropClient(fd);
                        continue;
                    }
                    
                    auto it = clientSwapchains.find(id);
                    if (it == clientSwapchains.end() || index < 0 || (size_t)index >= it->second->images.size()) {
                        if (fence >= 0) close(fence);
                        continue;
                    }
                    
                    auto swapchain = it->second.get();
                    auto window = windowManager->getWindow(swapchain->windowId);
                    if (!window) {
                        if (fence >= 0) close(fence);
                        continue;
                    }
                    
                    auto image = swapchain->images[index];
                    
                    auto lock = presentLock.lock();   
                    
                    window->externalContent = image.get();
                    auto presentRequest = std::make_unique<PresentRequest>();
                    presentRequest->drawable = image.get();
                    presentRequest->keepAlive = image;
                    presentRequest->sync_fence = fence;
                    presentRequest->presentId = present_id;
                    presentRequest->clientFd = fd;
                    presentRequest->window = window;
                    presentRequest->swapchainId = id;
                    
                    presentRequests.push(std::move(presentRequest));
                    if (cache->onGuestPresent != nullptr && xServer->renderer != nullptr) {
                        env->CallVoidMethod(xServer->renderer, cache->onGuestPresent, window->windowObj);
                        if (env->ExceptionCheck()) env->ExceptionClear();
                    }
                    if (!presentRR) presentLock.notify();
                    break;
                }    
                case DESTROY_CLIENT_SWAPCHAIN: {
                    uint8_t id;
                    if (recv(fd, &id, 1, MSG_WAITALL) != 1) {
                        dropClient(fd);
                        continue;
                    }
                    
                    auto it = clientSwapchains.find(id);
                    if (it == clientSwapchains.end())
                        continue;
                    
                    auto window = windowManager->getWindow(it->second->windowId);
                    if (window) window->externalContent = nullptr;
                    clientSwapchains.erase(it);
                    break;
                }
                default:
                    break;            
            }
        }
    }
    
    printf("Stopping networkThread");
    for (int fd : clients) close(fd);
    for (auto &entry : clientSwapchains) {
        auto window = windowManager->getWindow(entry.second->windowId);
        if (window) window->externalContent = nullptr;
    }
    clientSwapchains.clear();
    close(server_fd);
    close(efd);
    cache->detachEnv();
}

void SurfaceDirect::eventThreadLoop() {
    bool restoreState = false;
    this->env = cache->getEnv();
    
    while (true) {
        std::function<void()> func = nullptr;
        
        auto lock = eventLock.lock();
        eventLock.wait(lock, [&]{ 
            // Queued events wait for a usable surface; counting them earlier would spin this loop at full speed.
            return stopped || state != State::NONE || cursorUpdate ||
                   (!eventQueue.empty() && hasSurface && surfaceChanged && !paused);
        });
        
        if (stopped) {
            printf("Stopping eventThread");
            lock.unlock();
            teardownControls();
            cache->detachEnv();
            return;
        }
        
        auto currState = state;
        state = State::NONE;
        
        if (currState == State::PAUSE) {
            printf("Received state PAUSE");
            paused = true;
            eventLock.notify();
        }
            
        if (currState == State::RESUME) {
            printf("Received state RESUME");
            paused = false;
            restoreState = true;
            eventLock.notify();
        }
        
        if (currState == State::CREATE_SURFACE) {
            printf("Received state CREATE_SURFACE");
            hasSurface = createRootWindowControl();
            // Windows made on an earlier surface hang off the old root control; hang them on the new one.
            restoreState = true;
            eventLock.notify();
        }
            
        if (currState == State::CHANGE_SURFACE) {
            printf("Received state CHANGE_SURFACE");
            resizeRootWindow();
            surfaceChanged = true;
            eventLock.notify();
        }
            
        if (hasSurface && restoreState) {
            printf("Restoring state after resume");
            restoreControlState();
            restoreState = false;
        }
            
        if (currState == State::DESTROY_SURFACE) {
            printf("Received state DESTROY_SURFACE");
            hasSurface = false;
            surfaceChanged = false;
            destroyRootWindowControl();
            eventLock.notify();
        }
        
        if (!eventQueue.empty() && hasSurface && surfaceChanged && !paused) {
            func = eventQueue.front();
            eventQueue.pop();
        }
        
        lock.unlock();
        
        if (func) {
            func();
            {
                auto l = presentLock.lock();
                eventsPending--;
            }
            presentLock.notify();
        }
        
        if (cursorUpdate) {
            if (hasSurface && !paused) updateCursorPosition();
            cursorUpdate = false;
        }
    }
}

int64_t SurfaceDirect::getCurrentTimeNanos() {
    struct timespec ts{};
    
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000000000LL + ts.tv_nsec;
}

void SurfaceDirect::onCommitCallback(void *context, ASurfaceTransactionStats *stats) {
    auto *self = reinterpret_cast<SurfaceDirect *>(context);
    if (!self->isPerformanceHintAPIAvailable() || !self->performanceHintSession || !self->performanceHintManager || !self->perfMode)
        return;
    
    if (self->previousReportedWorkTime == 0) {
        auto currentTime = self->getCurrentTimeNanos();
        self->previousReportedWorkTime = currentTime;
        return;
    }     
   
    auto currentTime = self->getCurrentTimeNanos();
    auto elapsed = currentTime - self->previousReportedWorkTime;
    pfnAPerformanceHintReportActualWorkDuration(self->performanceHintSession, elapsed);
    self->previousReportedWorkTime = currentTime;
}

void SurfaceDirect::onCompleteCallback(void *context, ASurfaceTransactionStats *stats) {
    std::unique_ptr<OnCompleteContext> completeContext(static_cast<OnCompleteContext *>(context));
    
    for (auto &request : completeContext->requests) {
        if (request->clientFd < 0) continue;

        // Same byte layout as three separate writes, but sent in one piece and without SIGPIPE if the guest is gone.
        int requestCode = 4;
        char message[13];
        memcpy(message, &requestCode, 4);
        memcpy(message + 4, &request->swapchainId, 1);
        memcpy(message + 5, &request->presentId, 8);
        send(request->clientFd, message, sizeof(message), MSG_NOSIGNAL);
    }
}

void SurfaceDirect::presentThreadLoop() {
    ASurfaceTransaction *presentTransaction = pfnASurfaceTransactionCreate();
    
    if (isPerformanceHintAPIAvailable() && perfMode) {
        performanceHintManager = pfnAPerformanceHintGetManager();
    }
    
    if (performanceHintManager != nullptr) {
        float targetFloat = xServer->refreshRate * 100.0f;
        int64_t targetWorkDuration = static_cast<int64_t>(1000000000.0f / targetFloat);
    
        int tid = gettid();
        std::vector<int32_t> tids{tid};
        performanceHintSession = pfnAPerformanceHintCreateSession(performanceHintManager,
            tids.data(), tids.size(), targetWorkDuration);
    }     
    
    while(true) {
        auto lock = presentLock.lock();
        
        presentLock.wait(lock, [&]{ 
            return stopped || (eventsPending == 0 && ((requestUpdate && presentRR) || (!presentRequests.empty() && !presentRR)) && hasSurface && surfaceChanged && !paused);
        });
        
        if (stopped) {
            printf("Stopping presentThread");
            break;
        }
        
        std::queue<std::unique_ptr<PresentRequest>> requests;
        
        while (!presentRequests.empty()) {
            auto presentRequest = presentRequests.pop();
            requests.push(std::move(presentRequest));
        }
        
        if (vsyncId > -1 && pfnASurfaceTransactionSetFrameTimeline) pfnASurfaceTransactionSetFrameTimeline(presentTransaction, vsyncId);
        
        if (presentRR) requestUpdate = false;
        lock.unlock();
        
        auto completeContext = std::make_unique<OnCompleteContext>();
        
        while (!requests.empty()) {
            auto presentRequest = std::move(requests.front());
            requests.pop();
            
            auto window = presentRequest->window;
            auto drawable = presentRequest->drawable;
            if (!window || !window->control || !drawable) {
                // Nothing to hand the fence to, so it must not leak.
                if (presentRequest->sync_fence >= 0) close(presentRequest->sync_fence);
                continue;
            }
        
            if (!window->enabled) {
                pfnASurfaceTransactionSetBuffer(presentTransaction, window->control, nullptr, presentRequest->sync_fence);
            }
            else {
                if (effectComposer->isSuitableForColorSwap(drawable) && drawable->composerTexture != nullptr) {
                    pfnASurfaceTransactionSetBuffer(presentTransaction, window->control, drawable->composerTexture->dstBuffer, presentRequest->sync_fence);
                }
                else {
                    pfnASurfaceTransactionSetBuffer(presentTransaction, window->control, drawable->ahb, presentRequest->sync_fence);
                }
                
                pfnASurfaceTransactionSetBufferTransparency(presentTransaction, window->control, ASURFACE_TRANSACTION_TRANSPARENCY_OPAQUE);
                if (drawable->isSurfaceDirect) {
                   completeContext->requests.push_back(std::move(presentRequest));
                }    
                if (!window->backPressureEnabled && pfnASurfaceTransactionSetEnableBackPressure && backPressure)  {
                    pfnASurfaceTransactionSetEnableBackPressure(presentTransaction, window->control, true);
                    window->backPressureEnabled = true;
                }
            }
        }
        
        if (perfMode && pfnASurfaceTransactionSetOnCommit) pfnASurfaceTransactionSetOnCommit(presentTransaction, this, SurfaceDirect::onCommitCallback);
        if (!completeContext->requests.empty()) pfnASurfaceTransactionSetOnComplete(presentTransaction, completeContext.release(), SurfaceDirect::onCompleteCallback);
        pfnASurfaceTransactionApply(presentTransaction);
    }
    
    if (isPerformanceHintAPIAvailable() && performanceHintSession) {
        pfnAPerformanceHintCloseSession(performanceHintSession);
        performanceHintSession = nullptr;
    }
    pfnASurfaceTransactionDelete(presentTransaction);
}

bool SurfaceDirect::start() {
    void* handle = dlopen("libandroid.so", RTLD_NOW);
    if (handle == nullptr) return false;
    pfnASurfaceTransactionSetPosition = reinterpret_cast<PFNASURFACETRANSACTIONSETPOSITION>(dlsym(handle,"ASurfaceTransaction_setPosition"));
    pfnASurfaceTransactionSetBuffer = reinterpret_cast<PFNASURFACETRANSACTIONSETBUFFER>(dlsym(handle,"ASurfaceTransaction_setBuffer"));
    pfnASurfaceTransactionSetGeometry = reinterpret_cast<PFNASURFACETRANSACTIONSETGEOMETRY>(dlsym(handle,"ASurfaceTransaction_setGeometry"));
    pfnASurfaceTransactionSetZOrder = reinterpret_cast<PFNASURFACETRANSACTIONSETZORDER>(dlsym(handle,"ASurfaceTransaction_setZOrder"));
    pfnASurfaceTransactionSetVisibility = reinterpret_cast<PFNASURFACETRANSACTIONSETVISIBILITY>(dlsym(handle,"ASurfaceTransaction_setVisibility"));
    pfnASurfaceTransactionReparent = reinterpret_cast<PFNASURFACETRANSACTIONREPARENT>(dlsym(handle,"ASurfaceTransaction_reparent"));
    pfnASurfaceTransactionSetOnComplete = reinterpret_cast<PFNASURFACETRANSACTIONSETONCOMPLETE>(dlsym(handle,"ASurfaceTransaction_setOnComplete"));
    pfnASurfaceTransactionSetOnCommit = reinterpret_cast<PFNASURFACETRANSACTIONSETONCOMMIT>(dlsym(handle,"ASurfaceTransaction_setOnCommit"));
    pfnASurfaceTransactionSetEnableBackPressure = reinterpret_cast<PFNASURFACETRANSACTIONSETENABLEBACKPRESSURE>(dlsym(handle,"ASurfaceTransaction_setEnableBackPressure"));
    pfnASurfaceTransactionSetBufferAlpha = reinterpret_cast<PFNASURFACETRANSACTIONSETBUFFERALPHA>(dlsym(handle, "ASurfaceTransaction_setBufferAlpha"));
    pfnASurfaceTransactionSetBufferTransparency = reinterpret_cast<PFNASURFACETRANSACTIONSETBUFFERTRANSPARENCY>(dlsym(handle, "ASurfaceTransaction_setBufferTransparency"));
    pfnASurfaceTransactionStatsGetPresentFenceFd = reinterpret_cast<PFNASURFACETRANSACTIONSTATSGETPRESENTFENCEFD>(dlsym(handle, "ASurfaceTransactionStats_getPresentFenceFd"));
    pfnASurfaceTransactionSetFrameTimeline = reinterpret_cast<PFNASURFACETRANSACTIONSETFRAMETIMELINE>(dlsym(handle, "ASurfaceTransaction_setFrameTimeline"));

    pfnASurfaceTransactionCreate = reinterpret_cast<PFNASURFACETRANSACTIONCREATE>(dlsym(handle,"ASurfaceTransaction_create"));
    pfnASurfaceTransactionDelete = reinterpret_cast<PFNASURFACETRANSACTIONDELETE>(dlsym(handle,"ASurfaceTransaction_delete"));
    pfnASurfaceTransactionApply = reinterpret_cast<PFNASURFACETRANSACTIONAPPLY>(dlsym(handle,"ASurfaceTransaction_apply"));

    pfnASurfaceControlAcquire = reinterpret_cast<PFNASURFACECONTROLACQUIRE>(dlsym(handle,"ASurfaceControl_acquire"));
    pfnASurfaceControlRelease = reinterpret_cast<PFNASURFACECONTROLRELEASE>(dlsym(handle,"ASurfaceControl_release"));
    pfnASurfaceControlCreate = reinterpret_cast<PFNASURFACECONTROLCREATE>(dlsym(handle,"ASurfaceControl_create"));
    pfnASurfaceControlCreateFromWindow = reinterpret_cast<PFNASURFACECONTROLCREATEFROMWINDOW>(dlsym(handle,"ASurfaceControl_createFromWindow"));

    pfnAChoreographerGetInstance = reinterpret_cast<PFNACHOREOGRAPHERGETINSTANCE>(dlsym(handle,"AChoreographer_getInstance"));
    pfnAChoreographerPostFrameCallback64 = reinterpret_cast<PFNACHOREOGRAPHERPOSTFRAMECALLBACK64>(dlsym(handle,"AChoreographer_postFrameCallback64"));
    pfnAChoreographerPostVsyncCallback = reinterpret_cast<PFNACHOREOGRAPHERPOSTVSYNCCALLBACK>(dlsym(handle, "AChoreographer_postVsyncCallback"));
    pfnAChoreographerGetFrameTimelineVsyncId = reinterpret_cast<PFNACHOREOGRAPHERGETFRAMETIMELINEVSYNCID>(dlsym(handle, "AChoreographerFrameCallbackData_getFrameTimelineVsyncId"));
    pfnAChoreographerGetPreferredTimelineIndex = reinterpret_cast<PFNACHOREOGRAPHERGETPREFERREDTIMELINEINDEX>(dlsym(handle, "AChoreographerFrameCallbackData_getPreferredFrameTimelineIndex"));
    
    pfnAPerformanceHintGetManager = reinterpret_cast<PFNAPERFORMANCEHINTGETMANAGER>(dlsym(handle, "APerformanceHint_getManager"));
    pfnAPerformanceHintCreateSession = reinterpret_cast<PFNAPERFORMANCEHINTCREATESESSION>(dlsym(handle, "APerformanceHint_createSession"));
    pfnAPerformanceHintReportActualWorkDuration = reinterpret_cast<PFNAPERFORMANCEHINTREPORTACTUALWORKDURATION>(dlsym(handle, "APerformanceHint_reportActualWorkDuration"));
    pfnAPerformanceHintUpdateTargetWorkDuration = reinterpret_cast<PFNAPERFORMANCEHINTUPDATETARGETWORKDURATION>(dlsym(handle, "APerformanceHint_updateTargetWorkDuration"));
    pfnAPerformanceHintCloseSession = reinterpret_cast<PFNAPERFORMANCEHINTCLOSESESSION>(dlsym(handle, "APerformanceHint_closeSession"));
        
    // SurfaceControl and the choreographer callbacks only exist from Android 10; without them there is nothing to run on.
    if (!pfnASurfaceTransactionCreate || !pfnASurfaceTransactionDelete || !pfnASurfaceTransactionApply ||
        !pfnASurfaceTransactionSetBuffer || !pfnASurfaceTransactionSetZOrder || !pfnASurfaceTransactionSetVisibility ||
        !pfnASurfaceTransactionSetGeometry || !pfnASurfaceTransactionReparent || !pfnASurfaceTransactionSetOnComplete ||
        !pfnASurfaceTransactionSetBufferTransparency || !pfnASurfaceControlCreate || !pfnASurfaceControlCreateFromWindow ||
        !pfnASurfaceControlRelease || !pfnAChoreographerGetInstance || !pfnAChoreographerPostFrameCallback64) {
        printf("SurfaceControl is not available on this device");
        return false;
    }

    // Reap any previous session before its state is reused.
    join();
    stopped = false;
    state = State::NONE;
    paused = false;
    hasSurface = false;
    surfaceChanged = false;
    cursorUpdate = false;
    requestUpdate = false;
    fullscreen = false;
    vsyncId = -1;
    previousReportedWorkTime = 0;
    windowTransaction = nullptr;
    cursorTransaction = nullptr;
    performanceHintManager = nullptr;
    performanceHintSession = nullptr;
    {
        auto lock = eventLock.lock();
        std::queue<std::function<void()>>().swap(eventQueue);
    }
    {
        auto lock = presentLock.lock();
        eventsPending = 0;
        while (!presentRequests.empty()) {
            auto request = presentRequests.pop();
            if (request && request->sync_fence >= 0) close(request->sync_fence);
        }
    }

    stopEventFd = eventfd(0, EFD_CLOEXEC);
    if (stopEventFd < 0) {
        printf("Failed to create the stop eventfd");
        return false;
    }
        
    eventThread = std::thread(&SurfaceDirect::eventThreadLoop, this);
    networkThread = std::thread(&SurfaceDirect::networkThreadLoop, this);
    presentThread = std::thread(&SurfaceDirect::presentThreadLoop, this);
   
    this->choreographer = pfnAChoreographerGetInstance();
    
    if (precisePresentation && pfnAChoreographerPostVsyncCallback)
        pfnAChoreographerPostVsyncCallback(this->choreographer, SurfaceDirect::onVsyncCallback, this);
    else     
        pfnAChoreographerPostFrameCallback64(this->choreographer, SurfaceDirect::onFrameCallback64, this);
    return true;
}


void SurfaceDirect::stop() {
    // Set under each lock so a thread that has just checked its predicate cannot miss the wake-up.
    {
        auto lock = eventLock.lock();
        stopped = true;
        eventLock.notify();
    }
    {
        auto lock = presentLock.lock();
        presentLock.notify();
    }
    if (stopEventFd >= 0) {
        uint64_t one = 1;
        write(stopEventFd, &one, sizeof(one));
    }
}

void SurfaceDirect::join() {
    if (eventThread.joinable()) eventThread.join();
    if (networkThread.joinable()) networkThread.join();
    if (presentThread.joinable()) presentThread.join();
    if (stopEventFd >= 0) {
        close(stopEventFd);
        stopEventFd = -1;
    }
}

void SurfaceDirect::pause() {
    auto lock = eventLock.lock();
    if (stopped) return;
    state = State::PAUSE;
    eventLock.notify();
    eventLock.wait(lock, [&]{ return state == State::NONE || stopped; });
}

void SurfaceDirect::resume() {
    auto lock = eventLock.lock();
    if (stopped) return;
    state = State::RESUME;
    eventLock.notify();
    eventLock.wait(lock, [&]{ return state == State::NONE || stopped; });
}

void SurfaceDirect::createSurface(ANativeWindow *window) {
    auto lock = eventLock.lock();
    if (stopped) {
        ANativeWindow_release(window);
        return;
    }
    this->native_window = window;
    state = State::CREATE_SURFACE;
    eventLock.notify();
    eventLock.wait(lock, [&]{ return state == State::NONE || stopped; });
    // The root SurfaceControl made from the window holds its own reference.
    this->native_window = nullptr;
    ANativeWindow_release(window);
}

void SurfaceDirect::changeSurface(int width, int height) {
    auto lock = eventLock.lock();
    if (stopped) return;
    this->surfaceWidth = width;
    this->surfaceHeight = height;
    state = State::CHANGE_SURFACE;
    eventLock.notify();
    eventLock.wait(lock, [&]{ return state == State::NONE || stopped; });
}

void SurfaceDirect::destroySurface() {
    auto lock = eventLock.lock();
    if (stopped) return;
    this->native_window = nullptr;
    state = State::DESTROY_SURFACE;
    eventLock.notify();
    eventLock.wait(lock, [&]{ return state == State::NONE || stopped; });
}

void SurfaceDirect::queueEvent(std::function<void()> func) {
    auto lock = eventLock.lock();
    if (stopped) return;
    {
        auto l = presentLock.lock();
        eventsPending++;
    }
    eventQueue.push(func);
    eventLock.notify();
}

void SurfaceDirect::requestWindowUpdate(Window *window) {
    auto lock = presentLock.lock();
    
    if (!window || window->isHidden()) return;
    auto drawable = window->drawable.get();
    
    if (drawable != nullptr && effectComposer->isSuitableForColorSwap(drawable))
        effectComposer->apply(drawable);
    
    auto target = window->hasExternalContents() ? window->externalContent : drawable;
    if (target == nullptr) return;
        
    auto presentRequest = std::make_unique<PresentRequest>();
    presentRequest->drawable = target;
    presentRequest->sync_fence = -1;
    presentRequest->presentId = -1;
    presentRequest->clientFd = -1;
    presentRequest->window = window;
    
    presentRequests.push(std::move(presentRequest));
    if (!presentRR) presentLock.notify();
}

void SurfaceDirect::requestCursorUpdate() {
    if (!cursorVisible) return;
    this->cursorUpdate = true;
}

void SurfaceDirect::createWindowControl(Window *window) {
    if (!window->parent || !window->inputOutput || !window->parent->control || window->control) return;
        
    window->control = pfnASurfaceControlCreate(window->parent->control, "surfacedirect");
    if (!window->control) return;
    
    pfnASurfaceTransactionSetZOrder(windowTransaction, window->control, window->z_order);
    pfnASurfaceTransactionSetVisibility(windowTransaction, window->control, ASURFACE_TRANSACTION_VISIBILITY_HIDE);
    
    if (pfnASurfaceTransactionSetPosition) {
        pfnASurfaceTransactionSetPosition(windowTransaction, window->control, window->x, window->y);
    }
    else {
        ARect src{};
        ARect dst = {
            .left = window->x,
            .top = window->y,
            .right = window->x + window->width,
            .bottom = window->y + window->height
        };
        pfnASurfaceTransactionSetGeometry(windowTransaction, window->control, src, dst, 0);
    }  
    pfnASurfaceTransactionApply(windowTransaction);
}

void SurfaceDirect::destroyWindowControl(Window *window) {
    if (!window) return;
    if (!window->control) return;
    
    pfnASurfaceControlRelease(window->control);
    window->control = nullptr;
}

void SurfaceDirect::mapWindow(Window *window) {
    if (!window->control) return;
    
    if (!windowManager->getUnviewableWMClass().empty() && !strcmp(window->className.c_str(), windowManager->getUnviewableWMClass().c_str()))
        window->enabled = false;
    
    pfnASurfaceTransactionSetVisibility(windowTransaction, window->control, ASURFACE_TRANSACTION_VISIBILITY_SHOW);
    pfnASurfaceTransactionApply(windowTransaction);
}

void SurfaceDirect::unmapWindow(Window *window) {
    if (!window->control) return;
    
    pfnASurfaceTransactionSetVisibility(windowTransaction, window->control, ASURFACE_TRANSACTION_VISIBILITY_HIDE);
    pfnASurfaceTransactionApply(windowTransaction);
}

void SurfaceDirect::changeGeometry(Window *window, bool resized) {
    if (!window->control) return;
    
    if (resized)
        pfnASurfaceTransactionSetBuffer(windowTransaction, window->control, nullptr, -1);
    
    if (pfnASurfaceTransactionSetPosition) {
        pfnASurfaceTransactionSetPosition(windowTransaction, window->control, window->x, window->y);
    }
    else {
        ARect src{};
        ARect dst = {
            .left = window->x,
            .top = window->y,
            .right = window->x + window->width,
            .bottom = window->y + window->height
        };
        pfnASurfaceTransactionSetGeometry(windowTransaction, window->control, src, dst, 0);
    }
    
    pfnASurfaceTransactionApply(windowTransaction);
}

void SurfaceDirect::changeZOrder(Window *window) {
    if (!window->control) return;
    
    pfnASurfaceTransactionSetZOrder(windowTransaction, window->control, window->z_order);
    pfnASurfaceTransactionApply(windowTransaction);
}

void SurfaceDirect::updateCursor(Cursor *cursor) {
    if (!cursorManager->control || !cursorTransaction) return;
    
    pfnASurfaceTransactionSetBuffer(cursorTransaction, cursorManager->control, cursor && (cursor->visible && cursorVisible) ? cursor->image->ahb : nullptr, -1);
    pfnASurfaceTransactionApply(cursorTransaction);
}


void SurfaceDirect::updateCursorPosition() {
    if (!cursorManager->control || !cursorTransaction) return;
    
    // Long-lived native thread: the local reference has to be released or the table fills up.
    Window *pointWindow = nullptr;
    jobject pointWindowObj = env->CallObjectMethod(xServer->inputDeviceManager, cache->getPointWindow);
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (pointWindowObj != nullptr) {
        jint id = env->GetIntField(pointWindowObj, cache->windowID);
        env->DeleteLocalRef(pointWindowObj);
        pointWindow = windowManager->getWindow(id);
    }
    auto cursor = (pointWindow != nullptr) ? pointWindow->cursor : nullptr;
    auto rootCursor = cursorManager->getRootCursor();
    auto rootWindow = windowManager->getRootWindow();
    if (!rootWindow) return;
    
    int x = std::clamp(cursorManager->pointer.posX, 0, rootWindow->width - 1);
    int y = std::clamp(cursorManager->pointer.posY, 0, rootWindow->height - 1);
    
    if (cursorVisible || (cursor && cursor->visible)) {
        if (pfnASurfaceTransactionSetPosition) {
            pfnASurfaceTransactionSetPosition(cursorTransaction, cursorManager->control, x, y);
        }
        else {
            Drawable *cursorImage = (cursor != nullptr) ? cursor->image.get() : (rootCursor != nullptr ? rootCursor->image.get() : nullptr);
            if (!cursorImage) return;
            
            ARect src{};
            ARect dst = {
                .left = x,
                .top = y,
                .right = x + cursorImage->width,
                .bottom = y + cursorImage->height
            };
            pfnASurfaceTransactionSetGeometry(cursorTransaction, cursorManager->control, src, dst, 0);
        }
        
        pfnASurfaceTransactionApply(cursorTransaction);
    }
    else {
        pfnASurfaceTransactionSetVisibility(cursorTransaction, cursorManager->control, ASURFACE_TRANSACTION_VISIBILITY_HIDE);
        pfnASurfaceTransactionApply(cursorTransaction);
    }
}

void SurfaceDirect::createRootCursorControl() {
    int ret;
    
    auto rootCursor = cursorManager->getRootCursor();
    auto rootWindow = windowManager->getRootWindow();
    if (!rootCursor || !rootWindow) return;
    
    if (!rootWindow->control) return;
    
    cursorManager->control = pfnASurfaceControlCreate(rootWindow->control, "surfacedirect");
    if (!cursorManager->control) return;
    
    cursorTransaction = pfnASurfaceTransactionCreate();
}

void SurfaceDirect::showCursor() {
    if (!cursorManager->control) createRootCursorControl();
    if (!cursorManager->control || !cursorTransaction) return;
    
    auto rootCursor = cursorManager->getRootCursor();
    if (!rootCursor) return;
    
    pfnASurfaceTransactionSetBuffer(cursorTransaction, cursorManager->control, rootCursor->image->ahb, -1);
    pfnASurfaceTransactionSetVisibility(cursorTransaction, cursorManager->control, cursorVisible ? ASURFACE_TRANSACTION_VISIBILITY_SHOW : ASURFACE_TRANSACTION_VISIBILITY_HIDE);
    pfnASurfaceTransactionSetZOrder(cursorTransaction, cursorManager->control, INT32_MAX);
    pfnASurfaceTransactionApply(cursorTransaction);
}

void SurfaceDirect::reparentWindow(Window *window, Window *parent) {
    if (!window->control) return;
    
    pfnASurfaceTransactionReparent(windowTransaction, window->control, parent->control);
    if (pfnASurfaceTransactionSetPosition) {
        pfnASurfaceTransactionSetPosition(windowTransaction, window->control, window->x, window->y);
    }
    else {
        ARect src{};
        ARect dst = {
            .left = window->x,
            .top = window->y,
            .right = window->x + window->width,
            .bottom = window->y + window->height
        };
        pfnASurfaceTransactionSetGeometry(windowTransaction, window->control, src, dst, 0);
    }
    
    pfnASurfaceTransactionApply(windowTransaction);
}

bool SurfaceDirect::isPerformanceHintAPIAvailable() {
    return pfnAPerformanceHintGetManager &&
           pfnAPerformanceHintCreateSession &&
           pfnAPerformanceHintUpdateTargetWorkDuration &&
           pfnAPerformanceHintReportActualWorkDuration &&
           pfnAPerformanceHintCloseSession;
}

bool SurfaceDirect::createRootWindowControl() {
    auto rootWindow = windowManager->getRootWindow();
    if (!rootWindow || !this->native_window) return false;
    
    rootWindow->control = pfnASurfaceControlCreateFromWindow(this->native_window, "surfacedirect");
    if (!rootWindow->control) return false;
 
    windowTransaction = pfnASurfaceTransactionCreate();
    return windowTransaction != nullptr;
}

void SurfaceDirect::purgeWindow(Window *window) {
    auto lock = presentLock.lock();
    presentRequests.removeWindow(window);
}


void SurfaceDirect::destroyRootWindowControl() {
    this->native_window = nullptr;
    if (windowTransaction) {
        pfnASurfaceTransactionDelete(windowTransaction);
        windowTransaction = nullptr;
    }
    
    auto rootWindow = windowManager->getRootWindow();
    if (!rootWindow) return;
    if (!rootWindow->control) return;

    pfnASurfaceControlRelease(rootWindow->control);
    rootWindow->control = nullptr;
}


void SurfaceDirect::destroyRootCursorControl() {
    if (cursorTransaction) {
        pfnASurfaceTransactionDelete(cursorTransaction);
        cursorTransaction = nullptr;
    }
    
    if (!cursorManager->control) return;

    pfnASurfaceControlRelease(cursorManager->control);
    cursorManager->control = nullptr;
}

// Final cleanup on the event thread once the session is over: every SurfaceControl made here goes with it.
void SurfaceDirect::teardownControls() {
    auto rootWindow = windowManager->getRootWindow();
    
    {
        auto lock = windowManager->windowLock.lock();
        for (auto &entry : windowManager->getWindowTree()) {
            auto window = entry.second.get();
            if (window == rootWindow || !window->control) continue;
            pfnASurfaceControlRelease(window->control);
            window->control = nullptr;
        }
    }
    
    destroyRootCursorControl();
    destroyRootWindowControl();
}

void SurfaceDirect::resizeRootWindow() {
    auto rootWindow = windowManager->getRootWindow();
    if (!rootWindow) return;
    
    if (!rootWindow->control || !windowTransaction) return;
    
    viewTransformation.update(surfaceWidth, surfaceHeight, rootWindow->width, rootWindow->height);
    
    ARect src{};
    ARect dst{};
    
    if (fullscreen) {
        src.left = viewTransformation.viewOffsetX;
        src.top = viewTransformation.viewOffsetY;
        src.right = viewTransformation.viewOffsetX + viewTransformation.viewWidth;
        src.bottom = viewTransformation.viewOffsetY + viewTransformation.viewHeight;
        
        dst.left = 0;
        dst.top = 0;
        dst.right = surfaceWidth;
        dst.bottom = surfaceHeight;
    }
    else {
        src.left = 0;
        src.top = 0;
        src.right = surfaceWidth;
        src.bottom = surfaceHeight;
        
        dst.left = viewTransformation.viewOffsetX;
        dst.top = viewTransformation.viewOffsetY;
        dst.right = viewTransformation.viewOffsetX + viewTransformation.viewWidth;
        dst.bottom = viewTransformation.viewOffsetY + viewTransformation.viewHeight;
    }
    
    pfnASurfaceTransactionSetGeometry(windowTransaction, rootWindow->control, src, dst, 0);
    pfnASurfaceTransactionSetBuffer(windowTransaction, rootWindow->control, rootWindow->drawable ? rootWindow->drawable->ahb : nullptr, -1);
    pfnASurfaceTransactionSetZOrder(windowTransaction, rootWindow->control, rootWindow->z_order);
    pfnASurfaceTransactionApply(windowTransaction);
}

void SurfaceDirect::restoreControlState() {
    if (!windowTransaction) return;
    
    auto rootWindow = windowManager->getRootWindow();
    if (!rootWindow) return;
    
    auto lock = windowManager->windowLock.lock();
    const auto& windowTree = windowManager->getWindowTree();
    
    for (const auto& entry : windowTree) {
        auto window = entry.second.get();
        if (window == rootWindow) continue;
        if (!window->control || !window->parent || !window->parent->control) continue;
        
        pfnASurfaceTransactionReparent(windowTransaction, window->control, window->parent->control);
        pfnASurfaceTransactionApply(windowTransaction);
    }
    
    if (cursorManager->control && cursorTransaction && rootWindow->control) {
        pfnASurfaceTransactionReparent(cursorTransaction, cursorManager->control, rootWindow->control);
        pfnASurfaceTransactionApply(cursorTransaction);
    }
}

void SurfaceDirect::toggleFullscreen() {
    auto rootWindow = windowManager->getRootWindow();
    if (!rootWindow || !rootWindow->control || !windowTransaction) return;
    
    fullscreen = !fullscreen;
    
    ARect src{};
    ARect dst{};
    
    if (fullscreen) {
        src.left = viewTransformation.viewOffsetX;
        src.top = viewTransformation.viewOffsetY;
        src.right = viewTransformation.viewOffsetX + viewTransformation.viewWidth;
        src.bottom = viewTransformation.viewOffsetY + viewTransformation.viewHeight;
        
        dst.left = 0;
        dst.top = 0;
        dst.right = surfaceWidth;
        dst.bottom = surfaceHeight;
    }
    else {
        src.left = 0;
        src.top = 0;
        src.right = surfaceWidth;
        src.bottom = surfaceHeight;
        
        dst.left = viewTransformation.viewOffsetX;
        dst.top = viewTransformation.viewOffsetY;
        dst.right = viewTransformation.viewOffsetX + viewTransformation.viewWidth;
        dst.bottom = viewTransformation.viewOffsetY + viewTransformation.viewHeight;
    }
    
    pfnASurfaceTransactionSetGeometry(windowTransaction, rootWindow->control, src, dst, 0);
    pfnASurfaceTransactionApply(windowTransaction);
}

void SurfaceDirect::setPerformanceMode(bool perfMode) {
    this->perfMode = perfMode;
}

void SurfaceDirect::setPresentRR(bool presentRR) {
    this->presentRR = presentRR;
}

void SurfaceDirect::setBackPressure(bool backPressure) {
    this->backPressure = backPressure;
}

void SurfaceDirect::setPrecisePresentation(bool precisePresentation) {
    this->precisePresentation = precisePresentation;
}

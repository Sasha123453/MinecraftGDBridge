#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <ws2tcpip.h>
#include <Geode/Geode.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include <Geode/modify/MenuLayer.hpp>
#include <Geode/modify/AppDelegate.hpp>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/CCScheduler.hpp>
#include <Geode/modify/FMODAudioEngine.hpp>
#include <Geode/cocos/support/zip_support/ZipUtils.h>
#include <chrono>
#include <atomic>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <fstream>
#include <cmath>
#include <cstdlib>
#include <unordered_map>
#include <unordered_set>
#include <algorithm>
#include <cctype>
#include <memory>
#include <Geode/cocos/platform/CCImage.h>

using namespace geode::prelude;
using Clock = std::chrono::steady_clock;
namespace {
constexpr char workspaceRoot[] = "C:/Users/shelk/Documents/Codex/2026-10-02/minecraft-java-geometry-dash-nasgubb-xo";
struct CustomMusicConfig { bool enabled=false; std::string path; int offsetMs=0; float volume=1.f; } customMusic;
bool musicPlayInit=false;
std::filesystem::path musicConfigPath() { return std::filesystem::path(workspaceRoot)/"outputs/bridge/runtime/gd-music-config.json"; }
bool configureMusic(matjson::Value const& request,bool persist) {
    try {
        CustomMusicConfig next;
        next.enabled=request["enabled"].asBool().unwrapOr(false);
        next.path=request["path"].asString().unwrapOr("");
        double offset=request["offset"].asDouble().unwrapOr(0),volume=request["volume"].asDouble().unwrapOr(1);
        if(!std::isfinite(offset) || offset<0 || offset>86400 || !std::isfinite(volume) || volume<0 || volume>1) throw std::runtime_error("Invalid music offset/volume");
        next.offsetMs=static_cast<int>(offset*1000); next.volume=static_cast<float>(volume);
        if(next.enabled || !next.path.empty()) {
            auto allowed=std::filesystem::weakly_canonical(std::filesystem::path(workspaceRoot)/"outputs/music");
            auto selected=std::filesystem::canonical(std::filesystem::path(next.path));
            auto relative=selected.lexically_relative(allowed);
            if(relative.empty() || relative.is_absolute() || *relative.begin()==".." || !std::filesystem::is_regular_file(selected)) throw std::runtime_error("Music must be a file under outputs/music");
            auto extension=selected.extension().string();std::transform(extension.begin(),extension.end(),extension.begin(),[](unsigned char c){return static_cast<char>(std::tolower(c));});
            if(extension!=".mp3" && extension!=".ogg" && extension!=".wav")throw std::runtime_error("Music extension must be MP3, OGG or WAV");
            auto size=std::filesystem::file_size(selected);if(size==0 || size>256ull*1024*1024)throw std::runtime_error("Music size must be 1 byte to 256 MiB");
            next.path=selected.generic_string();
        }
        if(persist) {
            auto value=matjson::Value::object();value["enabled"]=next.enabled;value["path"]=next.path;value["offset"]=next.offsetMs/1000.0;value["volume"]=next.volume;
            auto target=musicConfigPath();std::filesystem::create_directories(target.parent_path());auto temporary=target;temporary+=".tmp";
            {std::ofstream file(temporary,std::ios::binary);file<<value.dump();if(!file.good())throw std::runtime_error("Cannot save music config");}
            if(!MoveFileExW(temporary.c_str(),target.c_str(),MOVEFILE_REPLACE_EXISTING|MOVEFILE_WRITE_THROUGH))throw std::runtime_error("Cannot publish music config");
        }
        customMusic=std::move(next);log::info("Bridge custom native music enabled={} path={} offsetMs={} volume={} (applies at next native music start)",customMusic.enabled,customMusic.path,customMusic.offsetMs,customMusic.volume);return true;
    }catch(std::exception const& error){log::warn("Bridge music config rejected: {}",error.what());return false;}
}
void loadCustomMusic() {
    try {std::ifstream file(musicConfigPath(),std::ios::binary);if(!file)return;std::string text((std::istreambuf_iterator<char>(file)),{});if(text.size()>16384)return;auto parsed=matjson::parse(text);if(parsed)configureMusic(parsed.unwrap(),false);}catch(std::exception const& error){log::warn("Bridge music config load failed: {}",error.what());}
}
bool customMusicActive(int channel) { return channel==0 && customMusic.enabled && !customMusic.path.empty() && (musicPlayInit || PlayLayer::get()); }
}
$on_mod(Loaded) {
    loadCustomMusic();
    try {
        auto local = std::getenv("LOCALAPPDATA");
        if (local) {
            auto backup = Mod::get()->getSaveDir()/"first-run-backup";
            std::filesystem::create_directories(backup);
            for (auto name : {"CCGameManager.dat", "CCLocalLevels.dat"}) {
                auto source = std::filesystem::path(local)/"GeometryDash"/name;
                if (std::filesystem::exists(source) && !std::filesystem::exists(backup/name))
                    std::filesystem::copy_file(source,backup/name);
            }
            log::info("Original GD saves preserved in {}", backup.string());
        }
    } catch (std::exception const& error) { log::warn("GD backup failed: {}",error.what()); }
}

namespace {
bool buildMode = false;
bool minecraftMenuPaused = false;
bool bridgeMusicPaused = false;
bool bridgePaused() { return buildMode || minecraftMenuPaused; }
void syncBridgeMusicPause(bool force=false) {
    auto play=PlayLayer::get();if(!play)return;
    bool hold=bridgePaused();if(!force && hold==bridgeMusicPaused)return;
    if(hold){play->handleButton(false,1,true);FMODAudioEngine::sharedEngine()->pauseAllMusic(true);}
    else if(!play->m_isPaused)FMODAudioEngine::sharedEngine()->resumeAllMusic();
    bridgeMusicPaused=hold;
}
bool minecraftAuthored = false;
uint64_t buildRevision = 0;
bool diagnosticNoclip=false;
Clock::time_point diagnosticNoclipDeadline{};
void disableDiagnosticNoclip() { diagnosticNoclip=false;diagnosticNoclipDeadline={}; }
bool diagnosticNoclipActive() {
    if(diagnosticNoclip && Clock::now()>=diagnosticNoclipDeadline){disableDiagnosticNoclip();log::info("Bridge diagnostic noclip expired; normal native deaths restored");}
    return diagnosticNoclip;
}
struct Transport {
    std::mutex mutex;
    std::condition_variable cv;
    std::string latest;
    bool ready = false;
    std::vector<std::string> commands;
    std::atomic<bool> connected{false};
    std::atomic<bool> running{true};
    std::thread worker;
    Transport() : worker([this] { run(); }) {}
    ~Transport() { running = false; cv.notify_all(); worker.join(); }
    void publish(std::string frame) {
        std::lock_guard lock(mutex);
        latest = std::move(frame); ready = true; cv.notify_one();
    }
    void run() {
        WSADATA data;
        if (WSAStartup(MAKEWORD(2,2), &data)) return;
        SOCKET socket = INVALID_SOCKET;
        std::string input;
        while (running) {
            if (socket == INVALID_SOCKET) {
                socket = ::socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
                DWORD timeout = 300;
                setsockopt(socket, SOL_SOCKET, SO_SNDTIMEO, reinterpret_cast<char*>(&timeout), sizeof(timeout));
                sockaddr_in addr{}; addr.sin_family = AF_INET; addr.sin_port = htons(18471);
                addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
                if (connect(socket, reinterpret_cast<sockaddr*>(&addr), sizeof(addr))) {
                    closesocket(socket); socket = INVALID_SOCKET;
                    std::this_thread::sleep_for(std::chrono::milliseconds(500)); continue;
                }
                BOOL one = TRUE; setsockopt(socket, IPPROTO_TCP, TCP_NODELAY, reinterpret_cast<char*>(&one), sizeof(one));
                connected = true;
            }
            // Check readable sockets even with no outbound frames (ordinary GD menu).
            // A graceful server FIN is readable with zero FIONREAD bytes.
            fd_set readable; FD_ZERO(&readable); FD_SET(socket,&readable);
            timeval pollTimeout{0,0};
            int poll = select(0,&readable,nullptr,nullptr,&pollTimeout);
            if (poll == SOCKET_ERROR) {
                closesocket(socket); socket = INVALID_SOCKET; connected = false; input.clear(); continue;
            }
            if (poll > 0) {
                char probe;
                int peek = recv(socket,&probe,1,MSG_PEEK);
                if (peek <= 0) {
                    closesocket(socket); socket = INVALID_SOCKET; connected = false; input.clear(); continue;
                }
                char bytes[4096];
                int n = recv(socket,bytes,sizeof(bytes),0);
                if (n <= 0) {
                    closesocket(socket); socket = INVALID_SOCKET; connected = false; input.clear(); continue;
                }
                input.append(bytes,n);
                size_t end;
                std::lock_guard lock(mutex);
                while ((end=input.find('\n')) != std::string::npos) {
                    commands.push_back(input.substr(0,end)); input.erase(0,end+1);
                }
            }
            std::string frame;
            {
                std::unique_lock lock(mutex);
                cv.wait_for(lock, std::chrono::milliseconds(100), [this]{ return ready || !running; });
                if (!ready) continue;
                frame = std::move(latest); ready = false;
            }
            size_t offset = 0;
            while (offset < frame.size()) {
                int n = send(socket, frame.data()+offset, static_cast<int>(frame.size()-offset), 0);
                if (n <= 0) { closesocket(socket); socket = INVALID_SOCKET; connected = false; break; }
                offset += n;
            }
        }
        if (socket != INVALID_SOCKET) closesocket(socket);
        WSACleanup();
    }
};
Transport& transport() { static Transport instance; return instance; }

// A PlayLayer initializer installs GD singleton state. Constructing it while
// the previous PlayLayer is still running lets old scene teardown and delayed
// follower callbacks access the new level's state. Retire the old scene first;
// advance only outside CCScheduler::update, after the director acknowledges it.
struct NativeSceneTransition {
    GJGameLevel* level=nullptr;
    CCScene* parking=nullptr;
    bool authored=false;
    size_t authoredObjects=0;
};
NativeSceneTransition sceneTransition;
void stopRetiringNodeActions(CCNode* node,std::unordered_set<CCNode*>& visited) {
    if(!node || !visited.insert(node).second)return;
    // Native culling detaches object sprites from the scene tree. Their
    // follower arrays retain nodes which must lose actions before the owner
    // objects are destroyed, just like ordinary scene children during cleanup.
    node->stopAllActions();
    node->unscheduleAllSelectors();
    if(auto children=node->getChildren())for(auto child:CCArrayExt<CCNode*>(children))stopRetiringNodeActions(child,visited);
    if(auto sprite=geode::cast::typeinfo_cast<CCSpritePlus*>(node);sprite && sprite->m_followers)
        for(auto follower:CCArrayExt<CCNode*>(sprite->m_followers))stopRetiringNodeActions(follower,visited);
}
bool queueNativeScene(GJGameLevel* level,bool authored=false,size_t count=0) {
    if(!level || sceneTransition.level){log::warn("Bridge scene request ignored: transition already pending");return false;}
    level->retain();sceneTransition.level=level;sceneTransition.authored=authored;sceneTransition.authoredObjects=count;
    return true;
}
void advanceNativeSceneTransition() {
    if(!sceneTransition.level)return;
    auto director=CCDirector::sharedDirector();
    if(!sceneTransition.parking) {
        std::unordered_set<CCNode*> visited;
        stopRetiringNodeActions(director->getRunningScene(),visited);
        if(auto play=PlayLayer::get()) {
            play->handleButton(false,1,true);
            if(play->m_objects)for(auto object:CCArrayExt<GameObject*>(play->m_objects)) {
                stopRetiringNodeActions(object,visited);
                if(object){stopRetiringNodeActions(object->m_colorSprite,visited);stopRetiringNodeActions(object->m_glowSprite,visited);}
            }
            stopRetiringNodeActions(play->m_player1,visited);stopRetiringNodeActions(play->m_player2,visited);
        }
        disableDiagnosticNoclip();buildMode=false;minecraftMenuPaused=false;
        auto parking=CCScene::create();parking->retain();sceneTransition.parking=parking;
        director->replaceScene(parking);
        log::info("Bridge scene retirement queued: {} native nodes, next level {}",visited.size(),sceneTransition.level->m_levelID.value());
        return;
    }
    if(director->getRunningScene()!=sceneTransition.parking)return;
    auto transition=sceneTransition;sceneTransition={};
    // The previous scene has now received native onExit/cleanup/release and
    // the autorelease pool has drained. No old PlayLayer survives this boundary.
    auto next=PlayLayer::scene(transition.level,false,false);
    if(next) {
        if(transition.authored)++buildRevision;
        director->replaceScene(next);
        log::info("Bridge native scene activated after retirement: level {} authored {} objects {} revision {}",transition.level->m_levelID.value(),transition.authored,transition.authoredObjects,buildRevision);
    }else{log::warn("Bridge native scene creation failed after retirement");director->replaceScene(MenuLayer::scene(false));}
    transition.level->release();transition.parking->release();
}


// Runs GD's own network downloader and creates an ordinary native PlayLayer.
// A retained level lives only for this request; existing saves are not replaced.
class NativeLevelLoader : public LevelDownloadDelegate, public MusicDownloadDelegate {
    LevelDownloadDelegate* previous = nullptr;
    GJGameLevel* pending = nullptr;
    bool busy = false;
    int requested = 0, song = 0;
    bool downloadMusic = true;
    Clock::time_point musicDeadline{};
    void launch() {
        MusicDownloadManager::sharedState()->removeMusicDownloadDelegate(this);
        auto level = pending; pending = nullptr; busy = false; musicDeadline = {};
        if (!level) return;
        queueNativeScene(level);level->release();
    }
public:
    void tick() {
        if(pending && musicDeadline!=Clock::time_point{} && Clock::now()>=musicDeadline) {
            log::warn("Bridge song {} download wait timed out after 12s; launching original level without waiting for music",song);
            launch();
        }
    }
    void beginData(matjson::Value const& request) {
        if (busy) { log::warn("Bridge native data request ignored: loader busy"); return; }
        int id=request["id"].asInt().unwrapOr(0);
        std::string compressed=request["levelString"].asString().unwrapOr("");
        if(id<=0 || compressed.empty() || compressed.size()>4*1024*1024 || compressed.find('\0')!=std::string::npos) {
            log::warn("Bridge native data request rejected: invalid ID or compressed payload"); return;
        }
        std::string raw=ZipUtils::decompressString(compressed,false,0);
        if(raw.empty() || raw.size()>20*1024*1024 || raw.find(';')==std::string::npos) {
            log::warn("Bridge native data request rejected: invalid decoded level"); return;
        }
        auto level=GJGameLevel::create();
        level->m_levelID=id;
        level->m_levelName=request["name"].asString().unwrapOr("Original Reference");
        level->m_creatorName=request["creator"].asString().unwrapOr("Original Reference");
        level->m_levelType=GJLevelType::Saved;
        level->m_dontSave=true; level->m_isEditable=false;
        level->m_songID=request["songId"].asInt().unwrapOr(0);
        level->m_audioTrack=request["audioTrack"].asInt().unwrapOr(0);
        level->m_objectCount=static_cast<int>(std::count(raw.begin(),raw.end(),';')-1);
        level->m_levelString=compressed;
        busy=true; requested=id; downloadMusic=request["downloadMusic"].asBool().unwrapOr(true);
        log::info("Bridge original native level data accepted: {} compressed bytes, {} records, ID {}",compressed.size(),level->m_objectCount,id);
        // Same retained native PlayLayer/music path as the official downloader; no saved-level insertion.
        levelDownloadFinished(level);
    }
    void begin(int id) {
        if (busy || id <= 0) return;
        auto manager = GameLevelManager::sharedState();
        // Do not take another screen's active level-download callback.
        if (manager->m_levelDownloadDelegate) {
            log::warn("Bridge level request delayed: another downloader owns the callback"); return;
        }
        busy = true; requested = id; downloadMusic = true;
        if (auto saved = manager->getSavedLevel(id); saved && !saved->m_levelString.empty()) {
            levelDownloadFinished(saved); return;
        }
        previous = manager->m_levelDownloadDelegate;
        manager->m_levelDownloadDelegate = this;
        manager->downloadLevel(id,false,0);
        log::info("Bridge requested native level download {}",id);
    }
    void levelDownloadFinished(GJGameLevel* level) override {
        auto manager = GameLevelManager::sharedState();
        if (manager->m_levelDownloadDelegate == this) manager->m_levelDownloadDelegate = previous;
        previous = nullptr;
        if (!level || static_cast<int>(level->m_levelID.value()) != requested) { busy = false; return; }
        pending = level; pending->retain(); song = level->m_songID;
        auto music = MusicDownloadManager::sharedState();
        if (song <= 0 || music->isSongDownloaded(song) || !downloadMusic || customMusic.enabled) { launch(); return; }
        musicDeadline=Clock::now()+std::chrono::seconds(12);
        music->addMusicDownloadDelegate(this);
        if (music->getSongInfoObject(song)) music->downloadSong(song);
        else music->getSongInfo(song,false);
        log::info("Bridge level ready; downloading native song {}",song);
    }
    void levelDownloadFailed(int response) override {
        auto manager = GameLevelManager::sharedState();
        if (manager->m_levelDownloadDelegate == this) manager->m_levelDownloadDelegate = previous;
        previous = nullptr; busy = false;
        log::warn("Bridge native level download failed: {}",response);
    }
    void loadSongInfoFinished(SongInfoObject* object) override {
        if (pending && object && object->m_songID == song) MusicDownloadManager::sharedState()->downloadSong(song);
    }
    void loadSongInfoFailed(int id, GJSongError) override {
        if (pending && id == song) { log::warn("Bridge song metadata unavailable; launching without music"); launch(); }
    }
    void downloadSongFinished(int id) override { if (pending && id == song) launch(); }
    void downloadSongFailed(int id, GJSongError) override {
        if (pending && id == song) { log::warn("Bridge song unavailable; launching without music"); launch(); }
    }
};
NativeLevelLoader& levelLoader() { static NativeLevelLoader instance; return instance; }


std::unordered_map<GLuint,std::string> exportedTextures;
std::unordered_map<GLuint,std::unique_ptr<CCImage>> sampledAtlases;
struct AtlasColor {ccColor3B color{255,255,255};bool saturated=false;};
std::unordered_map<std::string,AtlasColor> sampledFrameColors;
std::string exportTexture(CCTexture2D* texture) {
    if (!texture) return "";
    auto id = texture->getName();
    if (auto found = exportedTextures.find(id); found != exportedTextures.end()) return found->second;
    std::string output;
    auto textures = CCTextureCache::sharedTextureCache()->m_pTextures;
    for (auto [key,value] : CCDictionaryExt<const char*,CCTexture2D*>(textures)) {
        if (value != texture) continue;
        auto source = std::filesystem::path(std::string(CCFileUtils::sharedFileUtils()->fullPathForFilename(key,true)));
        std::error_code error;
        if (source.extension() != ".png" || !std::filesystem::exists(source,error)) continue;
        auto directory = std::filesystem::path("C:/Users/shelk/Documents/Codex/2026-10-02/minecraft-java-geometry-dash-nasgubb-xo/outputs/bridge/runtime");
        std::filesystem::create_directories(directory,error);
        auto target = directory/fmt::format("gd-atlas-{}-{}",id,source.filename().string());
        std::filesystem::copy_file(source,target,std::filesystem::copy_options::overwrite_existing,error);
        if (!error) { output = target.generic_string(); log::info("Bridge native texture exported: {}",output); }
        break;
    }
    exportedTextures[id] = output;
    return output;
}
AtlasColor actualAtlasColor(CCSprite* sprite) {
    if(!sprite || !sprite->getTexture())return {};
    auto texture=sprite->getTexture();auto quad=sprite->getQuad();
    float u0=1,v0=1,u1=0,v1=0;
    for(auto v:{quad.bl,quad.br,quad.tr,quad.tl}){u0=std::min(u0,v.texCoords.u);v0=std::min(v0,v.texCoords.v);u1=std::max(u1,v.texCoords.u);v1=std::max(v1,v.texCoords.v);}
    auto key=fmt::format("{}:{:.6f}:{:.6f}:{:.6f}:{:.6f}",texture->getName(),u0,v0,u1,v1);
    if(auto found=sampledFrameColors.find(key);found!=sampledFrameColors.end())return found->second;
    auto found=sampledAtlases.find(texture->getName());
    if(found==sampledAtlases.end()) {
        auto path=exportTexture(texture);auto image=std::make_unique<CCImage>();
        if(path.empty() || !image->initWithImageFile(path.c_str()) || image->getBitsPerComponent()!=8 || !image->getData())return {};
        found=sampledAtlases.emplace(texture->getName(),std::move(image)).first;
    }
    auto image=found->second.get();int width=image->getWidth(),height=image->getHeight(),channels=image->hasAlpha()?4:3;
    int x0=std::clamp(int(std::floor(u0*width)),0,width-1),x1=std::clamp(int(std::ceil(u1*width)),0,width);
    int y0=std::clamp(int(std::floor(v0*height)),0,height-1),y1=std::clamp(int(std::ceil(v1*height)),0,height);
    struct Bin {double weight=0,r=0,g=0,b=0;};std::array<Bin,24> bins{};
    int step=std::max(1,int(std::sqrt(std::max(1,(x1-x0)*(y1-y0))/4096.0)));
    auto data=image->getData();
    for(int y=y0;y<y1;y+=step)for(int x=x0;x<x1;x+=step) {
        auto p=data+(y*width+x)*channels;double alpha=channels==4?p[3]/255.0:1.0;if(alpha<0.10)continue;
        double r=p[0],g=p[1],b=p[2];
        if(image->isPremultipliedAlpha()){r=std::min(255.0,r/alpha);g=std::min(255.0,g/alpha);b=std::min(255.0,b/alpha);}
        double maximum=std::max({r,g,b}),minimum=std::min({r,g,b}),chroma=maximum-minimum;
        if(maximum<24 || chroma<32 || chroma/maximum<0.20)continue;
        double hue=maximum==r?(g-b)/chroma:maximum==g?2+(b-r)/chroma:4+(r-g)/chroma;
        if(hue<0)hue+=6;auto& bin=bins[std::clamp(int(hue*4),0,23)];double weight=alpha*chroma;
        bin.weight+=weight;bin.r+=r*weight;bin.g+=g*weight;bin.b+=b*weight;
    }
    auto best=std::max_element(bins.begin(),bins.end(),[](auto const& a,auto const& b){return a.weight<b.weight;});
    AtlasColor result;
    if(best->weight>0){result.saturated=true;result.color={static_cast<unsigned char>(std::lround(best->r/best->weight)),static_cast<unsigned char>(std::lround(best->g/best->weight)),static_cast<unsigned char>(std::lround(best->b/best->weight))};}
    sampledFrameColors[key]=result;return result;
}
std::string actualFrameName(GameObject* object) {
    auto value=ObjectToolbox::sharedState()->intKeyToFrame(object->m_objectID);
    return value?std::string(value):std::string();
}
int actualSpikePeaks(std::string_view frame) {
    // Counts verified from the installed native atlas alpha silhouettes.
    for(auto const& entry:std::array<std::pair<std::string_view,int>,7>{{{"pit_01_",6},{"pit_02_",6},{"pit_03_",7},{"d_spikes_01_",8},{"d_spikes_02_",7},{"d_spikes_03_",6},{"d_spikes_04_",4}}})
        if(frame.starts_with(entry.first))return entry.second;
    if(frame.starts_with("spike_") || frame.starts_with("invis_spike_") || frame.starts_with("gdh_spike_"))return 1;
    return 0;
}
CCPoint gdPoint(CCNode* node, CCPoint point, PlayerObject* player) {
    auto world = node->convertToWorldSpace(point);
    auto target=player?player->getParent():nullptr;
    if(!target)return world;
    auto play=PlayLayer::get();
    // Culled objects are detached from GD's camera layers: their world-space
    // transform already yields level coordinates. An inverse camera there added
    // the scrolling offset a second time. Apply it only to attached layer trees.
    for(auto p=node;p;p=p->getParent())
        if(p==target || (play && (p==play->m_objectLayer || p==play->m_inShaderObjectLayer || p==play->m_aboveShaderObjectLayer)))
            return target->convertToNodeSpace(world);
    return world;
}
matjson::Value vertex(CCPoint p,float u,float v,ccColor4B color) {
    auto a = matjson::Value::array();
    for (double n : {double(p.x),double(p.y),double(u),double(v),double(color.r),double(color.g),double(color.b),double(color.a)}) a.push(n);
    return a;
}
float unitOpacity(float value) { return std::isfinite(value)?std::clamp(value,0.f,1.f):0.f; }
float logicalSpriteOpacity(GameObject* object,CCNode* node) {
    bool detail=false,glow=false;
    for(auto p=node;p && p!=object;p=p->getParent()) {
        if(p==object->m_colorSprite)detail=true;
        if(p==object->m_glowSprite)glow=true;
    }
    auto color=detail?object->m_detailColor:object->m_baseColor;
    auto action=detail?object->m_detailActionSprite:object->m_mainActionSprite;
    int colorID=detail?object->m_activeDetailColorID:object->m_activeMainColorID;
    // Channel and group opacity are logical native state, separate from GD's
    // viewport alpha. Read them without activating objects or changing sprites.
    float opacity=color?unitOpacity(color->m_opacity):1.f;
    if(colorID>0 && action && action->m_colorAction) {
        opacity=unitOpacity(action->m_colorAction->m_currentOpacity);
        if(object->m_opacityGroupCount>0)opacity*=unitOpacity(object->groupOpacityMod());
    } else if(!color && object->m_opacityGroupCount>0)opacity*=unitOpacity(object->groupOpacityMod());
    if(glow)opacity*=unitOpacity(object->m_opacityMod);
    return unitOpacity(opacity);
}
unsigned char uncullSpriteAlpha(CCSprite* sprite,GameObject* object,std::unordered_set<CCNode*> const& roots) {
    float opacity=logicalSpriteOpacity(object,sprite);
    // getOpacity is intrinsic local alpha; displayed alpha includes culled roots.
    // Only those known roots are excluded. Child fades/invisibility remain native.
    for(auto p=static_cast<CCNode*>(sprite);p && p!=object;p=p->getParent()) {
        if(roots.contains(p))continue;
        if(auto rgba=geode::cast::typeinfo_cast<CCNodeRGBA*>(p))opacity*=rgba->getOpacity()/255.f;
    }
    return static_cast<unsigned char>(std::lround(unitOpacity(opacity)*255.f));
}
void avatarNodes(CCNode* node,PlayerObject* player,matjson::Value& layers,int depth = 0,std::unordered_set<CCNode*>* visited = nullptr,std::unordered_set<CCNode*> const* cullingRoots = nullptr,GameObject* visualObject = nullptr) {
    if (!node || ((!cullingRoots || !cullingRoots->contains(node)) && !node->isVisible()) || depth > 12 || layers.size() >= 96) return;
    if (visited && !visited->insert(node).second) return;
    auto children = node->getChildren();
    if (children) for (auto child : CCArrayExt<CCNode*>(children))
        if (child->getZOrder() < 0) avatarNodes(child,player,layers,depth+1,visited,cullingRoots,visualObject);
    if (auto sprite = geode::cast::typeinfo_cast<CCSprite*>(node); layers.size() < 96 && sprite && !sprite->getDontDraw() && sprite->getTexture()) {
        auto texture = sprite->getTexture(); auto image = exportTexture(texture);
        if (!image.empty()) {
            auto quad = sprite->getQuad();
            auto rect = sprite->getTextureRect(); auto offset = sprite->getOffsetPosition();
            // Batch-node quads may already contain batch transforms. Reconstruct the same local vertex rectangle.
            CCPoint local[4] = {{offset.x,offset.y},{offset.x+rect.size.width,offset.y},
                {offset.x+rect.size.width,offset.y+rect.size.height},{offset.x,offset.y+rect.size.height}};
            ccV3F_C4B_T2F q[4] = {quad.bl,quad.br,quad.tr,quad.tl};
            bool uncullAlpha=visualObject && cullingRoots && !visualObject->isVisible() && visualObject->getDisplayedOpacity()==0 && sprite->getDisplayedOpacity()==0;
            if(uncullAlpha) {
                auto alpha=uncullSpriteAlpha(sprite,visualObject,*cullingRoots);
                auto nativeColor=sprite->getDisplayedColor();
                for(auto& v:q) {
                    v.colors.a=alpha;
                    if(sprite->isOpacityModifyRGB()) {
                        v.colors.r=static_cast<unsigned char>(nativeColor.r*alpha/255);
                        v.colors.g=static_cast<unsigned char>(nativeColor.g*alpha/255);
                        v.colors.b=static_cast<unsigned char>(nativeColor.b*alpha/255);
                    }
                }
            }
            auto vertices = matjson::Value::array();
            for (int i=0;i<4;++i) vertices.push(vertex(gdPoint(sprite,local[i],player),q[i].texCoords.u,q[i].texCoords.v,q[i].colors));
            auto item = matjson::Value::object(); item["path"] = image; item["vertices"] = std::move(vertices);
            if(visualObject){item["alphaSource"]=uncullAlpha?"logical-native-opacity":"native-quad";item["cullingCorrected"]=uncullAlpha;item["nativeDisplayedOpacity"]=sprite->getDisplayedOpacity();}
            item["textureWidth"] = texture->getPixelsWide(); item["textureHeight"] = texture->getPixelsHigh();
            auto blend = sprite->getBlendFunc(); item["blendSource"] = blend.src; item["blendDestination"] = blend.dst;
            layers.push(std::move(item));
        }
    }
    if (children) for (auto child : CCArrayExt<CCNode*>(children))
        if (child->getZOrder() >= 0) avatarNodes(child,player,layers,depth+1,visited,cullingRoots,visualObject);
}
bool descendantOf(CCNode* node,CCNode* parent) {
    for(auto p=node;p;p=p->getParent())if(p==parent)return true;
    return false;
}
bool worldVisible(CCNode* node) {
    if(!node)return false;
    for(auto p=node;p;p=p->getParent())if(!p->isVisible())return false;
    return true;
}
void nativeObjectVisuals(GameObject* object,PlayerObject* player,matjson::Value& layers) {
    // Logical visibility is independent of GD's narrower native camera culling.
    // Bypass only the object/fill/glow root visibility bit, leaving descendants,
    // Native sprite dontDraw, UVs, colors and transforms remain untouched.
    // Export alpha excludes viewport culling only; logical alpha stays native.
    if(!object || object->m_isDisabled || object->m_isInvisible || object->m_isGroupDisabled || object->m_isGroupDisabledTemp) return;
    std::unordered_set<CCNode*> visited;
    std::unordered_set<CCNode*> cullingRoots{object};
    if(object->m_colorSprite)cullingRoots.insert(object->m_colorSprite);
    if(object->m_glowSprite && !object->m_hasNoGlow)cullingRoots.insert(object->m_glowSprite);
    avatarNodes(object,player,layers,0,&visited,&cullingRoots,object);
    // GD may attach colored fills/glows directly to a sprite batch parent.
    // Export their actual sprite quads, UVs and RGBA without inventing a material.
    for(auto sprite:{object->m_colorSprite,object->m_glowSprite})
        if(sprite && cullingRoots.contains(sprite) && !descendantOf(sprite,object) && (!sprite->getParent() || worldVisible(sprite->getParent())))
            avatarNodes(sprite,player,layers,0,&visited,&cullingRoots,object);
}
void streakMesh(CCMotionStreak* streak,PlayerObject* player,char const* kind,matjson::Value& trails) {
    if (!streak || !streak->isVisible() || streak->m_uNuPoints < 2 || !streak->m_pVertices || !streak->m_pTexCoords || !streak->m_pColorPointer) return;
    auto path = exportTexture(streak->getTexture());
    if (path.empty()) return;
    auto vertices = matjson::Value::array();
    unsigned n = std::min(streak->m_uNuPoints,1024u)*2;
    for (unsigned i=0;i<n;++i) {
        auto p = streak->m_pVertices[i]; auto uv = streak->m_pTexCoords[i]; auto c = streak->m_pColorPointer+i*4;
        vertices.push(vertex(gdPoint(streak,{p.x,p.y},player),uv.u,uv.v,{c[0],c[1],c[2],c[3]}));
    }
    auto mesh = matjson::Value::object(); mesh["kind"] = kind; mesh["topology"] = "strip";
    mesh["path"] = path; mesh["vertices"] = std::move(vertices);
    auto blend = streak->getBlendFunc(); mesh["blendSource"] = blend.src; mesh["blendDestination"] = blend.dst;
    mesh["textureWidth"] = streak->getTexture()->getPixelsWide(); mesh["textureHeight"] = streak->getTexture()->getPixelsHigh();
    trails.push(std::move(mesh));
}
void nativeVisuals(PlayerObject* player,matjson::Value& frame) {
    auto main=player->m_playerColor1,detail=player->m_playerColor2;
    auto primary=matjson::Value::array();primary.push(main.r);primary.push(main.g);primary.push(main.b);frame["primaryColor"]=std::move(primary);
    auto secondary=matjson::Value::array();secondary.push(detail.r);secondary.push(detail.g);secondary.push(detail.b);frame["secondaryColor"]=std::move(secondary);
    auto layers = matjson::Value::array(); avatarNodes(player,player,layers); frame["avatarLayers"] = std::move(layers);
    auto trails = matjson::Value::array();
    streakMesh(player->m_regularTrail,player,"regular",trails);
    streakMesh(player->m_shipStreak,player,"ship",trails);
    auto wave = player->m_waveTrail;
    if (wave && wave->isVisible() && wave->m_pBuffer && wave->m_nBufferCount > 0) {
        auto vertices = matjson::Value::array(); int n = std::min(wave->m_nBufferCount,6144); n -= n%3;
        for (int i=0;i<n;++i) { auto v=wave->m_pBuffer[i]; vertices.push(vertex(gdPoint(wave,{v.vertices.x,v.vertices.y},player),v.texCoords.u,v.texCoords.v,v.colors)); }
        auto mesh = matjson::Value::object(); mesh["kind"]="wave"; mesh["topology"]="triangles"; mesh["path"]=""; mesh["vertices"] = std::move(vertices); auto blend = wave->getBlendFunc(); mesh["blendSource"] = blend.src; mesh["blendDestination"] = blend.dst; trails.push(std::move(mesh));
    }
    frame["trails"] = std::move(trails);
}

char const* mode(PlayerObject* p) {
    if (p->m_isShip) return "ship";
    if (p->m_isBall) return "ball";
    if (p->m_isBird) return "ufo";
    if (p->m_isDart) return "wave";
    if (p->m_isRobot) return "robot";
    if (p->m_isSpider) return "spider";
    if (p->m_isSwing) return "swing";
    return "cube";
}
char const* kind(GameObjectType t) {
    switch (t) {
    case GameObjectType::Solid: case GameObjectType::Breakable: case GameObjectType::Slope: return "solid";
    case GameObjectType::Hazard: case GameObjectType::AnimatedHazard: return "hazard";
    case GameObjectType::YellowJumpPad: case GameObjectType::PinkJumpPad: case GameObjectType::GravityPad:
    case GameObjectType::YellowJumpRing: case GameObjectType::PinkJumpRing: case GameObjectType::GravityRing:
    case GameObjectType::GreenRing: case GameObjectType::DropRing: case GameObjectType::RedJumpPad:
    case GameObjectType::RedJumpRing: case GameObjectType::CustomRing: case GameObjectType::DashRing:
    case GameObjectType::GravityDashRing: case GameObjectType::SpiderOrb: case GameObjectType::SpiderPad:
    case GameObjectType::TeleportOrb: return "orb";
    case GameObjectType::InverseGravityPortal: case GameObjectType::NormalGravityPortal:
    case GameObjectType::ShipPortal: case GameObjectType::CubePortal: case GameObjectType::BallPortal:
    case GameObjectType::InverseMirrorPortal: case GameObjectType::NormalMirrorPortal:
    case GameObjectType::RegularSizePortal: case GameObjectType::MiniSizePortal: case GameObjectType::UfoPortal:
    case GameObjectType::DualPortal: case GameObjectType::SoloPortal: case GameObjectType::WavePortal:
    case GameObjectType::RobotPortal: case GameObjectType::TeleportPortal: case GameObjectType::SpiderPortal:
    case GameObjectType::SwingPortal: case GameObjectType::GravityTogglePortal: case GameObjectType::Modifier: return "portal";
    default: return "decor";
    }
}
std::vector<std::string> splitText(std::string const& value,char delimiter) {
    std::vector<std::string> parts; size_t start=0;
    for (;;) { auto end=value.find(delimiter,start); if(end==std::string::npos){parts.push_back(value.substr(start));break;} parts.push_back(value.substr(start,end-start));start=end+1; }
    return parts;
}
int recordID(std::string const& record) {
    auto values=splitText(record,',');
    for(size_t i=0;i+1<values.size();i+=2) if(values[i]=="1") {try{return std::stoi(values[i+1]);}catch(...){return 0;}}
    return 0;
}
double recordX(std::string const& record) {
    auto values=splitText(record,',');
    for(size_t i=0;i+1<values.size();i+=2)if(values[i]=="2"){try{return std::stod(values[i+1]);}catch(...){return 0;}}
    return 0;
}
double recordY(std::string const& record) {
    auto values=splitText(record,',');
    for(size_t i=0;i+1<values.size();i+=2)if(values[i]=="3"){try{return std::stod(values[i+1]);}catch(...){return 0;}}
    return 0;
}
double recordScalar(std::string const& record,std::string_view key,double fallback) {
    auto values=splitText(record,',');
    for(size_t i=0;i+1<values.size();i+=2)if(values[i]==key){try{auto value=std::stod(values[i+1]);return std::isfinite(value)?value:fallback;}catch(...){return fallback;}}
    return fallback;
}
// createWithKey alone leaves m_objectType at its default Solid value.
// Learn classification only from objects fully configured by the native level.
std::unordered_map<int,char const*> initializedObjectKinds;
bool physicalID(int id) {
    auto found=initializedObjectKinds.find(id);
    return found!=initializedObjectKinds.end() && std::string_view(found->second)!="decor" && std::string_view(found->second)!="unknown";
}
std::string blueprintObjectKey(int id,double x,double y) {
    return fmt::format("{}:{}:{}",id,std::llround(x*10),std::llround(y*10));
}
std::string objectRecord(matjson::Value const& item,int id,double x,double y,double rotation,double scale) {
    std::string record=item["data"].asString().unwrapOr("");
    if(record.size()>16384 || record.find(';')!=std::string::npos)record.clear();
    // An untouched Minecraft template must round-trip byte-for-byte, including
    // original flips, slope orientation and implicit editor defaults.
    if(!record.empty() && recordID(record)==id && std::abs(recordX(record)-x)<1e-6 && std::abs(recordY(record)+90-y)<1e-6 && std::abs(recordScalar(record,"6",0)-rotation)<1e-6 && std::abs(recordScalar(record,"32",1)-scale)<1e-6)return record+";";
    auto values=splitText(record,','); std::string output;
    for(size_t i=0;i+1<values.size();i+=2) {
        auto const& key=values[i];
        if(key.empty() || key=="1" || key=="2" || key=="3" || key=="6" || key=="32")continue;
        output+=key+","+values[i+1]+",";
    }
    // PlayLayer loads serialized editorY atworldY=editorY+90.
    return output+fmt::format("1,{},2,{},3,{},6,{},32,{};",id,x,y-90.0,rotation,scale);
}

}

class $modify(BridgePlayLayer, PlayLayer) {
    struct StableVisual {float width=1,height=1;ccColor3B color{255,255,255};std::string frameName;int spikePeaks=0;bool atlasColor=false;};
    struct Fields {
        Clock::time_point frameTime{}, objectsTime{}, logTime{};
        uint64_t seq = 0;
        size_t objectCount = 0;
        bool blueprintExported=false;
        bool levelCompleted=false;
        bool levelCompletedDiagnosticNoclip=false;
        bool objectSetupFinished=false;
        std::unordered_set<int> visualDiagnostics;
        Clock::time_point rateTime{},cadenceLogTime{};
        uint64_t postUpdates=0,ratePostUpdates=0,ratePublished=0;
        double postUpdateHz=0,publishedHz=0;
        matjson::Value objectActivations=matjson::Value::array();
        std::unordered_map<int,StableVisual> stableVisuals;
        matjson::Value objectVisualDiagnostics=matjson::Value::array();
    };
    StableVisual stableVisual(GameObject* object) {
        auto found=m_fields->stableVisuals.find(object->m_uniqueID);
        if(found==m_fields->stableVisuals.end() || object->isVisible()) {
            auto name=actualFrameName(object);auto size=object->getContentSize();
            if(!name.empty())if(auto frame=CCSpriteFrameCache::sharedSpriteFrameCache()->spriteFrameByName(name.c_str())){auto original=frame->getOriginalSize();if(original.width>0 && original.height>0)size=original;}
            auto rgb=object->m_colorSprite?object->m_colorSprite->getDisplayedColor():object->getDisplayedColor();
            StableVisual visual{std::max(1.f,size.width*std::abs(object->getScaleX())),std::max(1.f,size.height*std::abs(object->getScaleY())),rgb,name,actualSpikePeaks(name)};
            auto type=std::string_view(kind(object->m_objectType));
            if(type=="orb" || type=="portal") {
                auto baked=actualAtlasColor(object);
                if(!baked.saturated && object->m_colorSprite)baked=actualAtlasColor(object->m_colorSprite);
                if(baked.saturated){visual.color={static_cast<unsigned char>(baked.color.r*rgb.r/255),static_cast<unsigned char>(baked.color.g*rgb.g/255),static_cast<unsigned char>(baked.color.b*rgb.b/255)};visual.atlasColor=true;}
            }
            return m_fields->stableVisuals[object->m_uniqueID]=visual;
        }
        return found->second;
    }
    bool init(GJGameLevel* level,bool replay,bool dontCreateObjects) {
        disableDiagnosticNoclip();
        minecraftAuthored = level && level->m_dontSave && level->m_creatorName == "Minecraft Bridge" && level->m_levelID.value() == 0;
        buildMode = false;
        minecraftMenuPaused=false;bridgeMusicPaused=false;
        musicPlayInit=true;
        bool initialized = PlayLayer::init(level,replay,dontCreateObjects);
        musicPlayInit=false;
        return initialized;
    }
    void createObjectsFromSetupFinished() {
        PlayLayer::createObjectsFromSetupFinished();
        // Setup callbacks occur inside initialization. Export only after a completed
        // native postUpdate, when runtime object geometry can be read safely.
        m_fields->objectSetupFinished=true;
    }
    void exportBlueprint() {
        if (!m_level || !m_objects) return;
        try {
            int id=static_cast<int>(m_level->m_levelID.value());
            auto blueprint=matjson::Value::object(); auto objects=matjson::Value::array();
            std::string compressed=m_level->m_levelString;
            if(auto null=compressed.find('\0');null!=std::string::npos)compressed.resize(null);
            std::string raw=compressed.empty()?"":std::string(ZipUtils::decompressString(compressed,false,0));
            std::string header=raw.substr(0,raw.find(';'));
            if(header.empty() || raw.size()>20*1024*1024)throw std::runtime_error("Native level serialized input unavailable or oversized");
            blueprint["v"]=1; blueprint["levelId"]=id;blueprint["name"]=std::string(m_level->m_levelName);
            blueprint["songId"]=m_level->m_songID;blueprint["audioTrack"]=m_level->m_audioTrack;
            blueprint["songPath"]=std::string(MusicDownloadManager::sharedState()->pathForSong(m_level->m_songID));
            blueprint["length"]=m_levelLength;blueprint["levelString"]=compressed;
            blueprint["rawLevelString"]=raw;blueprint["header"]=header;
            std::unordered_map<std::string,std::vector<GameObject*>> nativeObjects;
            initializedObjectKinds.clear();
            size_t nativePhysical=0;
            for(auto object:CCArrayExt<GameObject*>(m_objects)) {
                if(!object || object==m_anticheatSpike)continue;
                auto type=kind(object->m_objectType);
                stableVisual(object);
                auto known=initializedObjectKinds.find(object->m_objectID);
                if(known==initializedObjectKinds.end())initializedObjectKinds[object->m_objectID]=type;
                else if(std::string_view(known->second)!=type)known->second="unknown";
                if(std::string_view(type)=="decor")continue;
                ++nativePhysical;
                auto p=object->getPosition();nativeObjects[blueprintObjectKey(object->m_objectID,p.x,p.y)].push_back(object);
            }
            size_t sourceRecords=0,unmatchedSource=0;
            auto records=splitText(raw,';');
            for(size_t index=1;index<records.size();++index) {
                auto const& record=records[index];if(record.empty())continue;
                int objectId=recordID(record);
                ++sourceRecords;
                double x=recordX(record),y=recordY(record)+90.0;
                if(!std::isfinite(x)||!std::isfinite(y)||std::abs(x)>1000000||std::abs(y)>1000000){++unmatchedSource;continue;}
                auto found=nativeObjects.find(blueprintObjectKey(objectId,x,y));
                if(found==nativeObjects.end() || found->second.empty()){++unmatchedSource;continue;}
                auto object=found->second.back();found->second.pop_back();
                auto type=kind(object->m_objectType);
                auto p=object->getPosition();auto size=object->getContentSize();auto rect=object->getObjectRect();
                auto item=matjson::Value::object();item["id"]=objectId;item["type"]=type;
                // Authoring transforms come from the exact matched source
                // record. Runtime slope normalization and duplicate native
                // objects at one position need not preserve editor rotation.
                item["x"]=x;item["y"]=y;item["rotation"]=recordScalar(record,"6",0);item["scale"]=recordScalar(record,"32",1);
                item["nativeRotation"]=object->getRotation();item["nativeScale"]=object->getScale();
                item["vw"]=size.width*std::abs(object->getScaleX());item["vh"]=size.height*std::abs(object->getScaleY());
                item["w"]=rect.size.width;item["h"]=rect.size.height;
                item["invisible"]=object->m_isInvisible;item["disabled"]=object->m_isDisabled;
                // Preserve the exact original input record. Runtime serialization
                // can touch editor-only state absent in a gameplay PlayLayer.
                item["data"]=record;objects.push(std::move(item));
            }
            size_t unmatchedNative=0;for(auto const& entry:nativeObjects)unmatchedNative+=entry.second.size();
            blueprint["sourceRecords"]=sourceRecords;blueprint["unmatchedSourceRecords"]=unmatchedSource;
            blueprint["nativePhysicalObjects"]=nativePhysical;blueprint["matchedPhysicalObjects"]=objects.size();blueprint["unmatchedNativePhysicalObjects"]=unmatchedNative;blueprint["recordSource"]="original-serialized-level";
            blueprint["objects"]=std::move(objects);
            auto directory=std::filesystem::path("C:/Users/shelk/Documents/Codex/2026-10-02/minecraft-java-geometry-dash-nasgubb-xo/outputs/bridge/levels");
            std::filesystem::create_directories(directory);
            auto target=directory/fmt::format("level-{}.json",id);
            auto temporary=target; temporary+=".tmp";
            {std::ofstream file(temporary,std::ios::binary);file<<blueprint.dump(matjson::NO_INDENTATION);if(!file.good())throw std::runtime_error("Cannot write blueprint");}
            if(!MoveFileExW(temporary.c_str(),target.c_str(),MOVEFILE_REPLACE_EXISTING|MOVEFILE_WRITE_THROUGH))throw std::runtime_error("Cannot publish blueprint atomically");
            m_fields->blueprintExported=true;
            log::info("Bridge safe native blueprint exported {}/{} native physical objects ({} unmatched native; {} original records, {} unmatched source/non-physical) to {}",blueprint["objects"].size(),nativePhysical,unmatchedNative,sourceRecords,unmatchedSource,target.string());
        }catch(std::exception const& error){log::warn("Bridge blueprint export failed: {}",error.what());}
    }
    void publishFrame(bool force = false) {
        if (!m_player1 || !m_level) return;
        auto now = Clock::now();
        if (!force && now-m_fields->frameTime < std::chrono::milliseconds(8)) return;
        m_fields->frameTime = now;
        auto pos = m_player1->getPosition();
        matjson::Value frame = matjson::Value::object();
        frame["v"] = 1;
        frame["seq"] = ++m_fields->seq;
        if(m_fields->rateTime==Clock::time_point{})m_fields->rateTime=now;
        double rateSeconds=std::chrono::duration<double>(now-m_fields->rateTime).count();
        if(rateSeconds>=1.0){
            m_fields->postUpdateHz=(m_fields->postUpdates-m_fields->ratePostUpdates)/rateSeconds;
            m_fields->publishedHz=(m_fields->seq-m_fields->ratePublished)/rateSeconds;
            m_fields->rateTime=now;m_fields->ratePostUpdates=m_fields->postUpdates;m_fields->ratePublished=m_fields->seq;
        }
        frame["nativePostUpdateHz"]=m_fields->postUpdateHz;frame["publishedHz"]=m_fields->publishedHz;frame["publishMinIntervalMs"]=8;
        frame["attempts"]=m_attempts;frame["jumps"]=m_jumps;
        auto held=m_player1->m_holdingButtons.find(1);frame["jumpHeld"]=held!=m_player1->m_holdingButtons.end()&&held->second;
        frame["verticalVelocity"]=m_player1->m_yVelocity;frame["onGround"]=m_player1->m_isOnGround;frame["touchedRing"]=m_player1->m_touchedRing;frame["touchedPad"]=m_player1->m_touchedPad;
        frame["t"] = std::chrono::duration<double>(now.time_since_epoch()).count();
        frame["level"] = static_cast<int>(m_level->m_levelID.value());
        frame["name"] = std::string(m_level->m_levelName);
        frame["x"] = pos.x; frame["y"] = pos.y;
        frame["nativeCameraX"]=m_gameState.m_cameraPosition.x;frame["nativeCameraY"]=m_gameState.m_cameraPosition.y;
        frame["nativeCameraWidth"]=m_cameraWidth;frame["nativeCameraHeight"]=m_cameraHeight;
        frame["nativeCameraCenterX"]=m_gameState.m_cameraPosition.x+m_cameraWidth/2;frame["nativeCameraCenterY"]=m_gameState.m_cameraPosition.y+m_cameraHeight/2;
        frame["nativeCameraZoom"]=m_gameState.m_cameraZoom;frame["nativeCameraAngle"]=m_gameState.m_cameraAngle;
        if(auto layer=m_player1->getParent()){auto origin=layer->convertToNodeSpace({0,0});frame["nativeCameraWorldOriginX"]=origin.x;frame["nativeCameraWorldOriginY"]=origin.y;}
        frame["rotation"] = m_player1->getRotation();
        frame["scale"] = m_player1->m_vehicleSize;
        frame["ground"] = 90;
        frame["mode"] = mode(m_player1);
        frame["dead"] = m_player1->m_isDead;
        frame["diagnosticNoclip"]=diagnosticNoclipActive();frame["diagnosticNoclipRemainingSeconds"]=diagnosticNoclip?std::max(0.0,std::chrono::duration<double>(diagnosticNoclipDeadline-now).count()):0.0;
        frame["paused"] = m_isPaused || bridgePaused();
        frame["minecraftMenuPaused"]=minecraftMenuPaused;frame["minecraftPaused"]=minecraftMenuPaused;frame["nativePaused"]=m_isPaused;frame["bridgeMusicPaused"]=bridgeMusicPaused;frame["levelCompleted"]=m_fields->levelCompleted;frame["levelCompletedDiagnosticNoclip"]=m_fields->levelCompletedDiagnosticNoclip;
        frame["source"] = minecraftAuthored ? "minecraft" : "geometry-dash";
        frame["buildRevision"] = buildRevision;
        frame["percent"] = getCurrentPercent();
        frame["customMusicEnabled"]=customMusic.enabled;frame["customMusicPath"]=customMusic.path;frame["customMusicOffset"]=customMusic.offsetMs/1000.0;frame["customMusicVolume"]=customMusic.volume;
        nativeVisuals(m_player1,frame);
        auto second=matjson::Value::object(); second["active"]=m_gameState.m_isDualMode && m_player2;
        if(m_gameState.m_isDualMode && m_player2){auto p=m_player2->getPosition();second["x"]=p.x;second["y"]=p.y;second["rotation"]=m_player2->getRotation();second["scale"]=m_player2->m_vehicleSize;second["mode"]=mode(m_player2);second["dead"]=m_player2->m_isDead;nativeVisuals(m_player2,second);}
        frame["player2"]=std::move(second);
        if (force || now-m_fields->objectsTime >= std::chrono::milliseconds(150)) {
            m_fields->objectsTime = now;
            matjson::Value objects = matjson::Value::array();
            auto objectLayers=matjson::Value::array();
            auto activations=matjson::Value::array();
            auto visualDiagnostics=matjson::Value::array();
            std::vector<GameObject*> nearby;
            if(m_objects)for(auto object:CCArrayExt<GameObject*>(m_objects))if(object && object!=m_anticheatSpike && std::abs(object->getPositionX()-pos.x)<=1200)nearby.push_back(object);
            auto priority=[](GameObject* object){auto type=std::string_view(kind(object->m_objectType));return type=="orb" || type=="portal"?0:type=="hazard"?1:2;};
            std::stable_sort(nearby.begin(),nearby.end(),[&](GameObject* a,GameObject* b){int ar=priority(a),br=priority(b);if(ar!=br)return ar<br;return std::abs(a->getPositionX()-pos.x)<std::abs(b->getPositionX()-pos.x);});
            for (auto object : nearby) {
                auto p = object->getPosition();
                if (std::abs(p.x-pos.x) > 1200) continue;
                auto type = kind(object->m_objectType);
                if (std::string_view(type) == "decor") continue;
                auto rect = object->getObjectRect();
                matjson::Value item = matjson::Value::object();
                item["id"] = object->m_uniqueID;
                item["objectId"] = object->m_objectID;
                item["type"] = type;
                item["x"] = rect.origin.x + rect.size.width/2;
                item["y"] = rect.origin.y + rect.size.height/2;
                item["w"] = std::max(1.f, rect.size.width);
                item["h"] = std::max(1.f, rect.size.height);
                item["rotation"] = object->getRotation();
                item["scale"] = object->getScale();
                item["noTouch"]=object->m_isNoTouch;item["groupDisabled"]=object->m_isGroupDisabled;item["groupDisabledTemp"]=object->m_isGroupDisabledTemp;
                item["disabled"]=object->m_isDisabled;item["passable"]=object->m_isPassable;item["invisible"]=object->m_isInvisible;item["invisibleBlock"]=object->m_isInvisibleBlock;
                bool active=!object->m_isDisabled && !object->m_isGroupDisabled && !object->m_isGroupDisabledTemp;
                item["collisionEnabled"]=active && !object->m_isNoTouch;
                item["visualOpacity"]=logicalSpriteOpacity(object,object);
                // DontDraw belongs to an individual batch sprite, not the
                // object's logical visibility (its fill/glow may still draw).
                item["visualEnabled"]=active && !object->m_isInvisible && !object->m_isInvisibleBlock && logicalSpriteOpacity(object,object)>0;
                item["nativeAdditive"]=object->getBlendFunc().dst==GL_ONE || (object->m_glowSprite && !object->m_hasNoGlow && object->m_glowSprite->getBlendFunc().dst==GL_ONE);
                // Separate sprite dimensions from the native collision AABB.
                auto visual = stableVisual(object);
                item["vx"] = p.x; item["vy"] = p.y;
                item["vw"] = visual.width;item["vh"] = visual.height;
                auto local=object->getContentSize();auto bodyCenter=gdPoint(object,{local.width/2,local.height/2},m_player1);
                item["bodyX"]=bodyCenter.x;item["bodyY"]=bodyCenter.y;item["bodyWidth"]=visual.width;item["bodyHeight"]=visual.height;
                item["bodyRotation"]=object->getRotation()+((object->isFlipY()!=(object->getScaleY()<0))?180.f:0.f);
                item["nativeFrameName"]=visual.frameName;item["visualShape"]=std::string_view(type)=="hazard" && visual.spikePeaks>0?(visual.spikePeaks>1?"spike-strip":"spike"):"native";item["spikePeaks"]=visual.spikePeaks;
                item["visualColorSource"]=visual.atlasColor?"native-atlas-pixels-times-displayed-tint":"native-displayed-tint";
                auto bodyRgb=matjson::Value::array();bodyRgb.push(visual.color.r);bodyRgb.push(visual.color.g);bodyRgb.push(visual.color.b);item["visualColor"]=std::move(bodyRgb);
                item["visualMetadataSource"]="initialized-native-object";
                item["nativeVisible"]=object->isVisible();item["nativeOpacity"]=object->getDisplayedOpacity();item["nativeDontDraw"]=object->getDontDraw();
                if((std::string_view(type)=="orb" || std::string_view(type)=="portal" || std::string_view(type)=="hazard") && objectLayers.size()<512){
                    auto nativeColor=visual.color;
                    auto rgb=matjson::Value::array();rgb.push(nativeColor.r);rgb.push(nativeColor.g);rgb.push(nativeColor.b);item["visualColor"]=std::move(rgb);
                    if(std::string_view(type)!="hazard"){
                        item["activatedP1"]=object->hasBeenActivatedByPlayer(m_player1);item["activatedP2"]=m_player2&&object->hasBeenActivatedByPlayer(m_player2);item["powered"]=object->m_isRingPoweredOn;
                        auto activation=matjson::Value::object();activation["id"]=object->m_uniqueID;activation["objectId"]=object->m_objectID;activation["x"]=p.x;activation["y"]=p.y;activation["p1"]=item["activatedP1"];activation["p2"]=item["activatedP2"];activation["powered"]=object->m_isRingPoweredOn;if(activations.size()<512)activations.push(std::move(activation));
                    }
                    auto layers=matjson::Value::array();nativeObjectVisuals(object,m_player1,layers);
                    size_t nativeAlphaVertices=0,exportAlphaVertices=0;
                    for(auto const& layer:layers.asArray().unwrap())for(auto const& vertex:layer["vertices"].asArray().unwrap())if(vertex[7].asDouble().unwrapOr(0)>0){++exportAlphaVertices;if(layer["alphaSource"].asString().unwrapOr("")!="logical-native-opacity")++nativeAlphaVertices;}
                    item["nativeSpriteLayers"]=layers.size();item["nativeNonzeroAlphaVertices"]=nativeAlphaVertices;item["exportNonzeroAlphaVertices"]=exportAlphaVertices;
                    if(visualDiagnostics.size()<128){auto diagnostic=matjson::Value::object();for(auto key:{"id","objectId","type","vx","vy","vw","vh","bodyX","bodyY","bodyWidth","bodyHeight","bodyRotation","nativeFrameName","visualShape","spikePeaks","visualColor","visualColorSource","collisionEnabled","noTouch","groupDisabled","groupDisabledTemp","disabled","passable","invisible","invisibleBlock","visualEnabled","visualOpacity","nativeAdditive","nativeVisible","nativeOpacity","nativeDontDraw","nativeSpriteLayers","nativeNonzeroAlphaVertices","exportNonzeroAlphaVertices"})diagnostic[key]=item[key];visualDiagnostics.push(std::move(diagnostic));}
                    if(!layers.asArray().unwrap().empty() && m_fields->visualDiagnostics.insert(object->m_objectID).second){
                        auto color=object->m_colorSprite;auto glow=object->m_glowSprite;
                        auto rgb=color?color->getDisplayedColor():ccColor3B{0,0,0};
                        log::info("Bridge native object visuals ID{} layers{} nativeVisible{} colorSprite{} detachedColor{} RGB({},{},{}) glowSprite{} detachedGlow{}",object->m_objectID,layers.size(),object->isVisible(),bool(color),color&&!descendantOf(color,object),rgb.r,rgb.g,rgb.b,bool(glow),glow&&!descendantOf(glow,object));
                    }
                    for(auto layer:layers.asArray().unwrap()){if(objectLayers.size()>=512)break;layer["id"]=object->m_uniqueID;layer["objectId"]=object->m_objectID;layer["type"]=type;objectLayers.push(std::move(layer));}
                }
                objects.push(std::move(item));
            }
            m_fields->objectCount = objects.size();
            m_fields->objectActivations=std::move(activations);
            m_fields->objectVisualDiagnostics=std::move(visualDiagnostics);
            frame["objects"] = std::move(objects);
            frame["objectLayers"] = std::move(objectLayers);
        }
        frame["objectActivations"]=m_fields->objectActivations;
        frame["objectVisualDiagnostics"]=m_fields->objectVisualDiagnostics;
        if(now-m_fields->cadenceLogTime>=std::chrono::seconds(10)){m_fields->cadenceLogTime=now;log::info("Bridge native cadence postUpdate={}Hz published={}Hz minInterval=8ms",m_fields->postUpdateHz,m_fields->publishedHz);}
        if (now-m_fields->logTime >= std::chrono::seconds(1)) {
            m_fields->logTime = now;
            auto stats = frame;
            stats.erase("objects"); stats.erase("objectLayers"); stats.erase("player2"); stats["objectCount"] = m_fields->objectCount;
            stats["avatarLayerCount"] = frame["avatarLayers"].size(); stats.erase("avatarLayers");
            stats["trailMeshCount"] = frame["trails"].size(); stats.erase("trails");
            std::ofstream(Mod::get()->getSaveDir()/"sender-status.json") << stats.dump();
            std::ofstream(std::filesystem::path("C:/Users/shelk/Documents/Codex/2026-10-02/minecraft-java-geometry-dash-nasgubb-xo/outputs/bridge/runtime/gd-telemetry.json")) << stats.dump(matjson::NO_INDENTATION);
        }
        transport().publish(frame.dump(matjson::NO_INDENTATION) + "\n");
    }
    void postUpdate(float dt) {
        ++m_fields->postUpdates;
        PlayLayer::postUpdate(dt);
        if(m_fields->objectSetupFinished && !m_fields->blueprintExported)exportBlueprint();
        publishFrame();
    }
    void pauseGame(bool unfocused) {
        PlayLayer::pauseGame(unfocused); publishFrame(true);
    }
    void levelComplete() {m_fields->levelCompletedDiagnosticNoclip=diagnosticNoclipActive();PlayLayer::levelComplete();m_fields->levelCompleted=true;publishFrame(true);}
    void resetLevel() {m_fields->levelCompleted=false;m_fields->levelCompletedDiagnosticNoclip=false;PlayLayer::resetLevel();syncBridgeMusicPause(true);publishFrame(true);}
    void destroyPlayer(PlayerObject* p, GameObject* o) {
        if(diagnosticNoclipActive() && p && (p==m_player1 || p==m_player2))return;
        PlayLayer::destroyPlayer(p,o); publishFrame(true);
    }
    void onQuit() {disableDiagnosticNoclip();minecraftMenuPaused=false;buildMode=false;bridgeMusicPaused=false;PlayLayer::onQuit();}
};


namespace {
void loadMinecraftLevel(matjson::Value const& request) {
    auto items = request["objects"].asArray();
    if (!items || items.unwrap().size() > 20000) { log::warn("Bridge invalid Minecraft build object list"); return; }
    std::string raw="kS38,1;";
    auto baseline=request["baseLevelString"].asString().unwrapOr("");
    auto baselineRaw=request["baseRawLevelString"].asString().unwrapOr("");
    if(baseline.size()>20*1024*1024 || baselineRaw.size()>20*1024*1024){log::warn("Bridge baseline too large");return;}
    if(auto null=baseline.find('\0');null!=std::string::npos)baseline.resize(null);
    double rangeMaxX=request["rangeMaxX"].asDouble().unwrapOr(100000);
    if(!std::isfinite(rangeMaxX) || rangeMaxX<30 || rangeMaxX>100000)rangeMaxX=100000;
    double rangeMinY=request["rangeMinY"].asDouble().unwrapOr(90),rangeMaxY=request["rangeMaxY"].asDouble().unwrapOr(1080);
    if(!std::isfinite(rangeMinY) || !std::isfinite(rangeMaxY) || rangeMinY < -100000 || rangeMaxY>100000 || rangeMinY>rangeMaxY){rangeMinY=90;rangeMaxY=1080;}
    if(!baselineRaw.empty() || !baseline.empty()) {
        std::string decoded=baselineRaw.empty()?std::string(ZipUtils::decompressString(baseline,false,0)):baselineRaw;
        if(auto null=decoded.find('\0');null!=std::string::npos)decoded.resize(null);
        auto records=splitText(decoded,';');
        if(!records.empty() && !records[0].empty()) {
            raw=records[0]+";";
            // Keep continuation, out-of-authoring-height physics, and all
            // non-physical/unknown records. Serialized editor Y is world Y-90.
            for(size_t i=1;i<records.size();++i)if(!records[i].empty()) {
                double x=recordX(records[i]),worldY=recordY(records[i])+90;
                if(!std::isfinite(x) || !std::isfinite(worldY) || x<0 || x>rangeMaxX || worldY<rangeMinY || worldY>rangeMaxY || !physicalID(recordID(records[i])))raw+=records[i]+";";
            }
        }
    }
    size_t count=0;
    for(auto const& object:items.unwrap()) {
        int id=object["id"].asInt().unwrapOr(1);
        double x=object["x"].asDouble().unwrapOr(0),y=object["y"].asDouble().unwrapOr(105);
        double rotation=object["rotation"].asDouble().unwrapOr(0),scale=object["scale"].asDouble().unwrapOr(1);
        // GD stores mirrored objects using signed key 32 scale. Preserve the
        // sign; accepting only positive scale deleted 208 original XO objects.
        if(!physicalID(id) || !std::isfinite(x) || !std::isfinite(y) || !std::isfinite(rotation) || !std::isfinite(scale) || std::abs(x)>100000 || std::abs(y)>100000 || std::abs(scale)<.01 || std::abs(scale)>100)continue;
        raw+=objectRecord(object,id,x,y,rotation,scale);++count;
    }
    // Non-colliding offscreen decoration gives the authored empty course a native finish boundary.
    if(baseline.empty() && baselineRaw.empty() && request.contains("rangeMaxX"))for(int candidate:{14,15,16,17,39,49,50}) {
        auto marker=GameObject::createWithKey(candidate);
        if(marker && marker->m_objectType==GameObjectType::Decoration){raw+=fmt::format("1,{},2,{},3,-900;",candidate,rangeMaxX);break;}
    }
    auto level = GJGameLevel::create();
    level->m_levelID = 0;
    level->m_levelName = request["name"].asString().unwrapOr("Minecraft Build");
    level->m_creatorName = "Minecraft Bridge";
    level->m_levelType = GJLevelType::Editor;
    level->m_dontSave = true;
    level->m_isEditable = false;
    level->m_audioTrack = request["audioTrack"].asInt().unwrapOr(0);
    level->m_songID = request["songId"].asInt().unwrapOr(0);
    level->m_objectCount = static_cast<int>(std::count(raw.begin(),raw.end(),';')-1);
    std::string compressedLevel=ZipUtils::compressString(raw,false,0);
    if(auto null=compressedLevel.find('\0');null!=std::string::npos)compressedLevel.resize(null);
    level->m_levelString = compressedLevel;
    if (level->m_levelString.empty()) { log::warn("Bridge build compression failed"); return; }
    queueNativeScene(level,true,count);
}
void drainCommands() {
    std::vector<std::string> commands;
    { std::lock_guard lock(transport().mutex); commands.swap(transport().commands); }
    for (auto const& command : commands) {
        auto parsed = matjson::parse(command);
        if (!parsed) continue;
        auto const& request = parsed.unwrap();
        auto cmd = request["cmd"].asString().unwrapOr("");
        auto input = request["input"].asString().unwrapOr("");
        if(cmd=="diagnostic-noclip" || input=="diagnostic-noclip") {
            bool enabled=request["enabled"].asBool().unwrapOr(request["active"].asBool().unwrapOr(false));
            double seconds=request["durationSeconds"].asDouble().unwrapOr(60);if(!std::isfinite(seconds))seconds=60;seconds=std::clamp(seconds,1.0,60.0);
            if(enabled && PlayLayer::get()){diagnosticNoclip=true;diagnosticNoclipDeadline=Clock::now()+std::chrono::duration_cast<Clock::duration>(std::chrono::duration<double>(seconds));log::warn("Bridge visual QA diagnostic noclip enabled for {}s; results are not normal physics validation",seconds);}
            else disableDiagnosticNoclip();
            continue;
        }
        if(cmd=="music-config" || input=="music-config"){configureMusic(request,true);continue;}
        if(cmd=="shutdown-gd" || input=="shutdown-gd"){
            disableDiagnosticNoclip();
            if(auto play=PlayLayer::get())play->handleButton(false,1,true);
            log::info("Bridge authorized native shutdown requested; saving GD data");
            geode::utils::game::exit(true);return;
        }
        if (cmd == "load-minecraft-level") { loadMinecraftLevel(request); continue; }
        if (cmd == "build-mode") {
            bool active = request["active"].asBool().unwrapOr(false);
            if (active && !buildMode) if (auto play = PlayLayer::get()) play->handleButton(false,1,true);
            buildMode = active;syncBridgeMusicPause();continue;
        }
        if(cmd=="minecraft-pause"){minecraftMenuPaused=request["active"].asBool().unwrapOr(false);syncBridgeMusicPause();continue;}
        if (input == "load-level-data" || cmd == "load-level-data") { levelLoader().beginData(request); continue; }
        if (input == "load-level") { levelLoader().begin(request["id"].asInt().unwrapOr(58825144)); continue; }
        auto play = PlayLayer::get();
        if (!play) continue;
        if (cmd == "input") input = "jump";
        if (input == "jump" && !bridgePaused()) play->handleButton(request["down"].asBool().unwrapOr(false),1,true);
        if (input == "restart") play->resetLevel();
    }
}
}
class $modify(BridgeScheduler, CCScheduler) {
    void update(float dt) { drainCommands();if(minecraftMenuPaused&&!transport().connected){minecraftMenuPaused=false;syncBridgeMusicPause();}diagnosticNoclipActive();levelLoader().tick();CCScheduler::update(dt);advanceNativeSceneTransition(); }
};
class $modify(BridgeNativeMusic, FMODAudioEngine) {
    void loadMusic(gd::string path,float speed,float unknown,float volume,bool loop,int musicID,int channelID,bool dontReset) {
        if(customMusicActive(channelID)){path=customMusic.path;volume*=customMusic.volume;log::info("Bridge FMOD custom override-load channel={} musicID={} offsetMs={} volume={}",channelID,musicID,customMusic.offsetMs,customMusic.volume);}
        FMODAudioEngine::loadMusic(path,speed,unknown,volume,loop,musicID,channelID,dontReset);
    }
    void queueStartMusic(gd::string path,float pitch,float unknown,float volume,bool loop,int start,int end,int fadeIn,int fadeOut,int musicID,bool p10,int channelID,bool noPrepare,bool dontReset) {
        if(customMusicActive(channelID)){path=customMusic.path;log::info("Bridge FMOD custom override-queue channel={} musicID={} offsetMs={}",channelID,musicID,customMusic.offsetMs);}
        FMODAudioEngine::queueStartMusic(path,pitch,unknown,volume,loop,start,end,fadeIn,fadeOut,musicID,p10,channelID,noPrepare,dontReset);
    }
    void startMusic(int start,int end,int fadeIn,int fadeOut,bool loop,int musicID,bool noResume,bool dontReset) {
        auto music=m_fmodMusic.find(musicID);
        if(music!=m_fmodMusic.end() && customMusicActive(music->second.m_channelID) && std::string(music->second.m_filePath)==customMusic.path){
            start=std::max(0,start)+customMusic.offsetMs;
            log::info("Bridge FMOD custom override-start channel={} musicID={} startMs={} offsetMs={}",music->second.m_channelID,musicID,start,customMusic.offsetMs);
        }
        FMODAudioEngine::startMusic(start,end,fadeIn,fadeOut,loop,musicID,noResume,dontReset);
        // A queued native start can run after a paused restart returned. Keep
        // bridge pause reasons effective for that new native music channel too.
        if(bridgePaused())syncBridgeMusicPause(true);
    }
};
class $modify(BridgeBaseLayer, GJBaseGameLayer) {
    void update(float dt) {
        if (bridgePaused() && static_cast<GJBaseGameLayer*>(this) == static_cast<GJBaseGameLayer*>(PlayLayer::get())) {
            static_cast<BridgePlayLayer*>(PlayLayer::get())->publishFrame(); return;
        }
        GJBaseGameLayer::update(dt);
    }
};

class $modify(BridgeAppDelegate, AppDelegate) {
    void applicationWillResignActive() {
        if (transport().connected) return;
        AppDelegate::applicationWillResignActive();
    }
    void applicationDidEnterBackground() {
        if (transport().connected) return;
        AppDelegate::applicationDidEnterBackground();
    }
};

class $modify(BridgeMenuLayer, MenuLayer) {
    bool init() {
        if (!MenuLayer::init()) return false;
        transport(); // Authoring commands work from the ordinary GD menu too.
        auto size = CCDirector::sharedDirector()->getWinSize();
        auto menu = CCMenu::create(); menu->setPosition({size.width-100.f, 32.f});
        auto label = CCLabelBMFont::create("MC Bridge POC", "bigFont.fnt"); label->setScale(.28f);
        auto button = CCMenuItemSpriteExtra::create(label, this, menu_selector(BridgeMenuLayer::onBridge));
        menu->addChild(button);
        auto xoLabel = CCLabelBMFont::create("MC Bridge XO", "bigFont.fnt"); xoLabel->setScale(.28f);
        auto xoButton = CCMenuItemSpriteExtra::create(xoLabel,this,menu_selector(BridgeMenuLayer::onXO));
        xoButton->setPosition({0,24.f}); menu->addChild(xoButton); addChild(menu, 999);
        // One-shot explicit experiment request: ordinary launches remain unchanged.
        auto marker = std::filesystem::path("C:/Users/shelk/Documents/Codex/2026-10-02/minecraft-java-geometry-dash-nasgubb-xo/outputs/bridge/gd/start-xo.flag");
        std::error_code markerError;
        if (std::filesystem::exists(marker,markerError)) {
            std::filesystem::remove(marker,markerError);
            if (!markerError) {
                transport(); // Connect to Minecraft before gameplay can lose focus.
                geode::Loader::get()->queueInMainThread([] { levelLoader().begin(58825144); });
            }
        }
        return true;
    }
    void onXO(CCObject*) { levelLoader().begin(58825144); }
    void onBridge(CCObject*) {
        auto level = GameLevelManager::sharedState()->getMainLevel(1,false);
        if (level) queueNativeScene(level);
    }
};

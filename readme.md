# 仿牛客网论坛项目

**Ngrok**：内网穿透的工具，简而言之就是将内网IP映射成对外可访问的域名。

# 注册登录模块

使用邮箱注册，开启smtp协议，465端口，使用spring框架集成的mail模块，**什么是JavaMailSender和JavaMailSenderImpl？**javaMailSender和JavaMailSenderImpl是Spring官方提供的集成邮件服务的接口和实现类，以简单高效的设计著称，目前是Java后端发送邮件和集成邮件服务的主流工具。

数据库中存储的密码是用户输入的密码和随机生成的盐值的和加密后的结果。用户类型type=0表示普通用户，type=1表示管理员；type=2表示版主。

**注册：**业务层负责注册模块的逻辑，包括对用户名、用户密码、邮箱的验证，以及存入用户表属性信息的设置【类型，状态，随机生成的`激活码`】和用户数据插入Mysql。还有使用spring整合的Mail模块发送激活邮件。注册成功或者失败的信息存在map里面，返回给表现层。如果没有错误信息，也就是注册成功了。表现层的model里面处理记录注册成功与否的信息还会记录对应的下一步的跳转目标路径target。

**激活：**表现层的controller接收请求路径中的参数userId和激活码code，业务层的逻辑：根据userId查找User对象，如果激活码相同且未激活就更新用户状态进行激活。

**登录：**

1. 表现层先验证`验证码`：验证码是由`kaptcha`工具生成的,可以对它进行格式设置

    - `生成验证码`，将验证码信息存入redis,redis的key是`kaptcha:kaptchaowner`其中kaptchaowner是随机字符串唯一标识验证码的归属者。设置redis中验证码信息的有效时间，这里redis充当session的作用。设置cookie存储验证码的归属信息也就是redis的key，并将cookie放入response中。验证码的图片信息也会放入response的输出流中传给浏览器，后续要根据浏览器存储在cookie中的rediskey从服务器的redis中取出对应的验证码字符串与浏览器输入的进行验证，验证成功表示浏览器输入的验证码与服务器生成的验证码是一致的。
    
    - `验证码的验证`:从请求路径中获取验证码对应的`cookie`，根据cookie的值也就是redis的key查询存储在redis中的验证码信息，与页面输入的进行对比。

   - `账号密码的验证：`主要验证逻辑在业务层：验证空值、是否已存在、是否未激活、密码是否正确。验证成功生成`登录凭证`,登陆凭证存储在`redis`中，登录凭证会记录用户Id、登录凭证的有效时间、状态和唯一标识这些信息。其中有效时间可以通过页面传来的是否记住密码进行设置，默认12h，记住密码100天。验证成功与否的信息都会存入map传给表现层。如果表现层返回的Map存在登录凭证就说明验证成功了，登录凭证会存入cookie里面，cookie放入response，cookie也会设置有效时间。`处理请求时，都会查询用户的登录凭证`

     > **浏览器cookie的篡改问题**
     >
     > cookie存放的是作为唯一标识的随机字符串，如果浏览器端的cookie被篡改了，新的cookie是被篡改后的一个随机字符串，请求发送到服务器端，在redis中根据这个随机字符串作为redis的key查找登录用户时，一般不会找到其他用户的信息伪装成其他用户登录，因为redis里存的登录凭证是登录成功的用户才会存入的，并不是所有用户都有对应的登录凭证信息，而且redis的key是一串32位的字母加数组的随机字符串，一般很难通过修改生成正确的其他已登录用户对应的随机字符串。也就是说通过篡改cookie中的登录凭证冒充其他用户登录的几率是非常小的。
   
   **登陆凭证的验证：**
   
    - `拦截器`在每次请求处理前，从request中获取登录凭证对应的的cookie，然后在`redis`中查询该登录凭证，根据登录凭证记录的用户Id获取当前用户，使用ThreadLocal存储当前用户，实现线程隔离，在本次请求中都持有用户数据。
    - `拦截器`实现`HandlerInterceptor`接口，重写以下三个主要方法：
        - `preHandle`：请求处理之前执行，用于根据request中的cookie在redis中查询登陆凭证获取当前用户信息，存入TreadLocal中，在本次请求中持有用户信息；
        - `postHandle:`请求处理之后，模板引擎调用之前执行，用于把当前持有的用户信息传入页面
        - `afterCompletion:`模板调用之后执行，用于清理ThreadLocal中的用户数据。
    - `拦截器需要配置拦截路径`，在`WebMvcConfigurer`接口的实现类中配置，通过重写`addInterceptors()`方法，注册拦截器，并配置拦截路径。忽略静态资源：`"/**/*.css","/**/*.js","/**/*.png"...`。

# Redis的应用

#### **使用Redis存储验证码**

因为验证码需要频繁的访问和刷新，对性能要求较高，而且使用redis可以方便地为验证码设置有效时间，而且再在分布式部署下,redis可以解决session不一致的问题。

#### 使用Redis存储登录凭证

每次处理请求时都要查询用户凭证，访问的频率非常高。

#### **使用Redis作为缓存**

- **流程：**查询用户信息是频率很高的行为，放入缓存可以提高响应速度。在业务层的查询用户方法中使用，查询用户时先从缓存里取，缓存里没有再从数据库取，然后把查到的数据放入缓存，设置缓存有效时间1个小时;数据变更时`先更新数据再清除缓存数据`。
- **缓存问题：**
    - 缓存穿透
    - 缓存击穿
    - 缓存雪崩

##### 主动刷新缓存有效时间

其实主动刷新缓存的有效时间很有必要，缓存击穿和缓存雪崩问题可以得到缓解。

https://www.cnblogs.com/ASPNET2008/p/8733087.html

**为什么不是先删除缓存，再更新数据库？**

因为先删除缓存，如果在更新操作还没commit的时候，另外一个线程进来读取数据，缓存查不到，查数据库并放入缓存。然后第一个更新操作commit了。`导致缓存是旧数据，而数据库是新数据`。且不会像前面提到的删除缓存失败那样方便做处理。

**为什么缓存不是更新而是删除？**

https://zhuanlan.zhihu.com/p/347181685

##### 缓存穿透

> **缓存穿透:**不断地对缓存和数据库中==都没有的数据==发起请求，最终都会落在数据库中.比如id=-1的数据

解决方法:

- 缓存层缓存空值，本项目直接在缓存中缓存空值

  将数据库中的空值也缓存到缓存层中，这样查询该空值就不会再访问DB，而是直接在缓存层访问就行。但是这样有个弊端就是缓存太多空值占用了更多的空间，可以通过给缓存层空值设立一个较短的过期时间来解决，例如60s。

  *关于缓存空值的问题，如果直接把null值存入缓存，那么下一次查询的时候会先查缓存发现是null值，还是会去数据库查，如果在数据库没有查到查询方法直接返回null,而缓存中存入`" "`空格【不成立】，下次再查相同的数据时，发现缓存里是空格就直接返回null。【不成立】*

- ==布隆过滤器==

  将数据库中所有的查询条件，放入布隆过滤器中，当一个查询请求过来时，先经过布隆过滤器进行查，如果判断请求查询值存在，则继续查询数据库；如果判断请求查询不存在，直接丢弃。

- 增加校验和拦截,id做基础校验,id<0直接拦截.

##### 缓存击穿

> **缓存击穿:**由于==并发用户过多==访问`同一条数据`,当缓存失效了,同时去数据库222222读数据,引起数据库压力瞬间增大.

解决方法:

- 热点数据永不过期
- 使用互斥锁,控制并发量

##### 缓存雪崩

> **缓存雪崩**:缓存中的数据==大批量同时过期==,而==查询数量巨大==,引起数据库压力过大甚至宕机.
>
> 和缓存击穿不同的是,缓冲雪崩是大量数据同时过期,缓存击穿是并发查询同一条数据.

解决方法:

- 缓存数据的过期时间设为随机离散值,避免同时过期.·
- 热点数据永不过期.
- 如果是分布式部署,热点数据可以均匀分布在不同的数据库中.

#### 布隆过滤器

> `布隆过滤器用来解决缓存穿透`
>
> BloomFilter
>
>  * ①将位数组进行初始化，每个位都设置为0。
>  * ②对于给定集合里面的每一个元素，依次通过k个哈希函数进行映射，产生k个哈希值对应位数组上面k个点，对应的位置都标记为1.
>  * ③查询W元素是否存在集合中的时候，同样将W通过哈希映射到位数组上的k个点。
     >    *      如果k个点的其中有一个点不为1，则可以判断该元素一定不存在集合中。
>    *      反之，如果k个点都为1，则该元素`可能`存在集合中。并不保证一定在待查集合中。
>
> 布隆过滤器主要用来判断待查元素W在给定集合H中是否存在，有一定的误判率，给定一个足够大的每一位都初始为0的位数组，通过K个哈希函数对集合H中的所有元素进行哈希值与数组长度取模的映射，映射的位置都标记为1，然后用相同的哈希函数对元素W在位数组上映射到K个点，只要这K个点有一个不为1，就可以判断元素W`一定不在`集合H中，反之，如果K个点都是1，说明元素W`可能在`集合H中，但不能保证一定在H中,因为这K个点可能是不同元素经过哈希映射得到的位置。

```java
package bloomfilter;

import org.junit.Test;

import java.util.BitSet;

/**
 * @Description 布隆过滤器,判断元素W是否在给定的集合中.
 * @Author Pengnan
 * @CreateTime 2021年05月10日 20:43:00
 */
public class SimpleBloomFilter {
    @Test
    public void testBloodFilter(){
        SimpleBloomFilter simpleBloomFilter = new SimpleBloomFilter();
        String[] data={"12","34","56","hello"};
        for(String d:data){
            simpleBloomFilter.add(d);
        }
        String[] test={"10","20","hello"};
        for(String t:test){
            System.out.println(t+":"+simpleBloomFilter.isContain(t));
        }
    }

        private static final int DEFAULT_SIZE = 2 << 32;
        //bit数组，用来存放key
        private static BitSet bitSet = new BitSet(DEFAULT_SIZE);
        //后面hash函数会用到，用来生成不同的hash值
        private static final int[] seeds = {3,7,11,31,37};

        public void add(Object key) {
            //在得到的哈希索引位置设为true
            //Arrays.stream(ints).forEach(i -> bitSet.set(hash(key, i)));//Sets the bit at the specified index to true.
            for(int seed:seeds){
                bitSet.set(hash(key,seed));
            }
        }

        public boolean isContain(Object key) {
            boolean result = true;
            for (int seed : seeds) {
                result = result && bitSet.get(hash(key, seed));
            }
            return result;
        }

        //hash函数，借鉴了hashmap的扰动算法
        private int hash(Object key, int seed) {//因为seed的参与,相当于是不同的hash函数
            int h;
            return key == null ? 0 : (seed * (DEFAULT_SIZE - 1) & ((h = key.hashCode()) ^ (h >>> 16)));
        }
}
```

# 数据库表的设计

==用户表、帖子表、评论表==

**用户表user**

Id是`主键索引`，用户名和邮箱是`非聚簇索引`。

| 字段            | 类型      | 含义                                   |
| --------------- | --------- | -------------------------------------- |
| id              | int       | 主键，自增                             |
| `  username`    | varchar   | 用户名，`非聚簇索引`                   |
| password        | varcher   | 密码                                   |
| salt            | varchar   | 加密盐值                               |
| `email`         | carchar   | 邮箱，`非聚簇索引 `                    |
| type            | int       | 用户类型：0-普通用户，1-管理员，2-版主 |
| status          | int       | 用户状态：0-未激活，1-已激活           |
| activation_code | varchar   | 激活码                                 |
| header_url      | varchar   | 头像                                   |
| create_time     | timestamp | 注册时间                               |

版主能置顶、加精；管理员能删除

**帖子表**discuss_post

id是主键索引，用户id是非聚簇索引

| 字段          | 类型      | 含义                             |
| ------------- | --------- | -------------------------------- |
| id            | INT       | 主键,自增                        |
| ` user_id`    | INT       | 发帖的用户id，`非聚簇索引`       |
| title         | varchar   | 帖子标题                         |
| content       | text      | 帖子内容                         |
| type          | INT       | 帖子类型：0-普通，1-置顶         |
| status        | INT       | 帖子状态：0-正常，1-精华，2-拉黑 |
| create_time   | timestamp | 发帖时间                         |
| comment_count | INT       | 评论数量                         |
| score         | DOUBLE    | 帖子分数                         |

**评论表comment**

用户id和实体id是非聚簇索引

| 字段        | 类型      | 含义                       |
| ----------- | --------- | -------------------------- |
| id          | int       | 主键,自增                  |
| `user_id `  | int       | 评论用户的id，`非聚簇索引` |
| entity_type | int       | 评论实体的类型             |
| `entity_id` | int       | 评论实体的Id，`非聚簇索引` |
| target_id   | int       | 评论目标的id               |
| content     | text      | 评论内容                   |
| status      | int       | 评论状态                   |
| create_time | timestamp | 评论创建的时间             |

# 发帖

帖子表

type     0-普通；2-置顶

status  0-正常；1-精华；2-拉黑

#### 过滤敏感词

==前缀树==

在计算机科学中,前缀树(Tie树),即字典树,又称单词査找树或键树,它是一种专门处理字符串匹配的数据结构。前缀树可以最大限度地减少无谓的字符串比较(空间换时间),提高查询效率。

特点：

- 前缀树的根节点不包含字符,除根节点之外的每一个子节点都包含一个字符。
- 从根节点到某一尾点路径上经过的字符连起来就是该节点对应的字符串。
- 每个节点的各个子节点包含的字符都不相同。

**前缀树的实现**

节点类：结束标志；子节点【使用HashMap，因为子节点不止一个，key是下级节点的字符，value是下级节点的节点对象】

【leetcode题】前缀树的插入，查找实现：

```java
class Trie {
    class TrieNode{
        private boolean isEnd=false;
        HashMap<Character,TrieNode> subNode=new HashMap<>();
        public void setEnd(){
            this.isEnd=true;
        }
        public boolean isEnd(){
            return isEnd;
        }
        public void setSubNode(char ch,TrieNode node){
            subNode.put(ch,node);
        }
        public TrieNode getSubNode(char ch){
            return subNode.get(ch);
        }
    }
    private TrieNode root;
    /** Initialize your data structure here. */
    public Trie() {
        root=new TrieNode();
    }
    
    /** Inserts a word into the trie. */
    public void insert(String word) {
        TrieNode temp=root;
        for(int i=0;i<word.length();i++){
            char ch=word.charAt(i);
            TrieNode sub=temp.getSubNode(ch);
            if(sub==null){
                sub=new TrieNode();
                temp.setSubNode(ch,sub);
            }
            temp=sub;
            if(i==word.length()-1){
                temp.setEnd();
            }
        }
    }
    
    /** Returns if the word is in the trie. */
    public boolean search(String word) {
        TrieNode temp=root;
        for(int i=0;i<word.length();i++){
            char ch=word.charAt(i);
            TrieNode sub=temp.getSubNode(ch);
            if(sub==null){
                return false;
            }
            temp=sub;
            if(temp.isEnd()&&i==word.length()-1){
                return true;
            }
        }
        return false;
    }
    
    /** Returns if there is any word in the trie that starts with the given prefix. */
    public boolean startsWith(String prefix) {
        TrieNode temp=root;
        for(int i=0;i<prefix.length();i++){
            char ch=prefix.charAt(i);
            TrieNode sub=temp.getSubNode(ch);
            if(sub==null)return false;
            temp=sub;
        }
        return true;
    }
}
```

**前缀树实现敏感词过滤：**

1. 先读取敏感词，构建前缀树

2. 在字符串中查找敏感词并替换，

    - `三指针`:

        - 指针1指向前缀树的根节点，
        - 指针2指向字符串中当前待查子段的起始位置，一直右移
        - 指针3指向字符串中当前待查子段的结束位置，在合适的时候右移和左移

    - 查找过程：

        - 开始时指针2和指针3都在字符串的起始位置，指针1在前缀树的根节点，确定下一级的节点中有没有指针3位置的字符：
            - 如果没有说明指针2和指针3之间的字符不是敏感词，指针2和指针3都移到指针2的下一位；
            - 如果有【说明以指针2起始的子串疑似敏感词】，指针1移到下一级节点【对应字符是指针3位置的字符】，指针3向右移一位判断下一位是否在指针1的下一级节点中，一直循环。
        - 直到指针3到某个位置后指针1的下一级节点不存在该字符，说明指针2到指针3之间的子串不在前缀树中，不是敏感词，指针2右移一位同时指针3移到指针2相同的位置，指针1回到根节点重新搜索。
        - 如果在查找过程中指针1对应的节点有结束标志，说明指针2到指针3之间的字串在前缀树上存在完整的字符串，是敏感词，就把指针2到指针3之间的子串替换成**，然后指针2和指针3都移到指针3的下一个位置，指针1回到根节点继续搜索。

        ```java
            /**
             * 过滤敏感词
             *
             * @param text 待过滤的文本
             * @return 过滤后的文本
             */
            public String filter(String text) {
                if (StringUtils.isBlank(text)) {
                    return null;
                }
                // 指针1
                TrieNode tempNode = rootNode;
                // 指针2
                int begin = 0;
                // 指针3
                int position = 0;
                // 结果
                StringBuilder sb = new StringBuilder();
        
                while (position < text.length()) {
                    char c = text.charAt(position);
        
                    // 跳过符号
                    if (isSymbol(c)) {
                        // 若指针1处于根节点,将此符号计入结果,让指针2向下走一步
                        //这一步可以不需要，因为在下一个if请况里包含着
        //                if (tempNode == rootNode) {
        //                    sb.append(c);
        //                    begin++;
        //                }
                        // 无论符号在开头或中间,指针3都向下走一步，跳过符号
                        position++;
                        continue;//下次循环
                    }
                    // 检查下级节点
                    tempNode = tempNode.getSubNode(c);
                    if (tempNode == null) {//c不是下级节点
                        // 以begin开头的字符串不是敏感词
                        sb.append(text.charAt(begin));
                        // 进入下一个位置
                        position = ++begin;//--------------非敏感词，移到begin的下一位继续检查
                        // 重新指向根节点
                        tempNode = rootNode;//指针1回到前缀树的根节点
                    } else if (tempNode.isKeywordEnd()) {
                        // 发现敏感词,将begin~position字符串替换掉
                        sb.append(REPLACEMENT);
                        // 进入下一个位置
                        begin = ++position;//--------------是敏感词，移到position的下一位继续检查
                        // 重新指向根节点
                        tempNode = rootNode;//指针1回到前缀树的根节点
                    } else {
                        // 检查下一个字符
                        position++;//-----------------疑似敏感词，position后移，继续检查
                    }
                }
                // 将最后一批字符计入结果
                sb.append(text.substring(begin));
        
                return sb.toString();
            }
        ```

# 点赞&关注

## 点赞

==数据存储：==

点赞需要存储两部分的数据：从帖子角度需要记录帖子收到的点赞的用户集合，从用户角度需要记录该用户收到的赞的个数。

1. 某个实体的赞：`like:entity:entityType:entityId -> set(userId)`
   - key是实体类型：实体Id
   - value使用`set`存储，存的是点赞的用户Id【PS：肯定是只有登录才能点赞】
   - 因为点赞的时候有`"已赞"`和`"未赞"`两种状态，需要用`set`存储，根据当前用户是否在点赞集合里面判断是否已经点赞。
2. 用户收到的赞：`like:user:userId -> int`
   - key是用户Id
   - value是该用户收到的赞的个数，用int存就行

==具体实现：==

1. **点赞：**如果当前用户`不在帖子的已赞集合`中，对帖子`点赞`，要维护两条数据：

   - 一方面要把当前用户放入帖子的点赞集合中
   - 另一方面，帖子作者收到赞的个数也要加一

2. **取消赞：**如果当前用户`在帖子的已赞集合`中，对帖子`取消赞`，也要维护两条数据：、

   - 一方面要把当前用户从帖子的已赞集合中移除	
   - 另一方面，帖子作者收到赞的个数要减一

3. **点赞的数量和状态：**因为帖子收到的已赞集合是存在Redis的`set`中的，可以很容易得到已赞的数量和是否已赞的状态

4. **影响帖子分数：**点赞操作会影响帖子分数，所以帖子发生点赞或者取消赞的操作时，会被放入Redis中，这一部分数据在Redis中使用`set`存储，记录发生影响分数操作的帖子Id,然后等到定时任务到的时候只更新这一部分的帖子分数，将这些帖子`pop`出来。

5. **一个事务中进行：**因为要维护两条Redis数据,所以在一条事务中执行

   ```java
   // 点赞
       public void like(int userId, int entityType, int entityId, int entityUserId) {
           redisTemplate.execute(new SessionCallback() {
               @Override
               public Object execute(RedisOperations operations) throws DataAccessException {
                   String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
                   String userLikeKey = RedisKeyUtil.getUserLikeKey(entityUserId);//实体所属用户的id，统计用户得到了多少赞
   
                   boolean isMember = operations.opsForSet().isMember(entityLikeKey, userId);
   
                   operations.multi();
   
                   if (isMember) {//取消赞
                       operations.opsForSet().remove(entityLikeKey, userId);
                       operations.opsForValue().decrement(userLikeKey);
                   } else {//点赞
                       operations.opsForSet().add(entityLikeKey, userId);
                       operations.opsForValue().increment(userLikeKey);
                   }
                   return operations.exec();
               }
           });
       }
   ```

## 关注

==数据存储：==

关注操作相关的数据需要存储两种数据：某个用户关注的实体和某个实体拥有的粉丝：

1. 某个用户关注的实体：`followee:userId:entityType -> zset(entityId,now)`
   - key是用户Id
   - value是`zset`类型存储的实体Id和关注时间，关注时间作为`zset`的分数
2. 某个实体拥有的粉丝：`follower:entityType:entityId -> zset(userId,now)`
   - key是实体类型和实体Id
   - value是`zset`类型存储的用户Id和关注时间，关注时间作为`zset`的分数

==具体实现：==

1. **关注：**如果没有关注过，进行关注操作：
   - 一方面当前用户关注的实体的有序集合需要加入关注的实体Id
   - 另一方面，被关注的这个实体也要更新自己的粉丝集合，把当前用户加入到实体的粉丝集合
   - 这两条数据都是`zset`有序集合，有序集合的分数是当前时间
2. **取关：**如果已经关注过了，进行取关操作：
   - 一方面当前用户取关的实体的有序集合需要移除关注的实体Id
   - 另一方面，被取关的的这个实体也要更新自己的粉丝集合，把当前用户移除实体的粉丝集合
3. **分数与排序：**zset的分数是关注时间，后面获取粉丝列表和关注列表的时候会用到，倒序排列，越晚关注分数越高倒序之后排在前面
   - `redisTemplate.opsForZSet().reverseRange(followeeKey, offset, offset + limit - 1);//`获得倒序`索引范围`的数据，也就是最新关注的在最前面.【分页查询，根据当前页的起始行，和每页包含的数据总量进行分页查询】
4. **一个事务中进行：**关注操作发生时，会同时维护这两种数据，所以需要在一个事务中进行。
5. **==分页==：**
   - 分页组件`Page`，包括：
     - 属性及对应的设置和获取的方法：当前页码、显示上限、数据总数【用于计算总页数】、查询路径【用于复用分页链接】；
     - 获取数据：获取当前页的起始行、获取总页数、获取起始页码、获取结束页码

#### Redis的事务

Redis不会回滚，所以不具有原子性。

[MULTI](http://redis.cn/commands/multi.html) 命令用于开启一个事务，它总是返回 `OK` 。 [MULTI](http://redis.cn/commands/multi.html) 执行之后， 客户端可以继续向服务器发送任意多条命令， 这些命令不会立即被执行， 而是被`放到一个队列`中， 当 [EXEC](http://redis.cn/commands/exec.html)命令被调用时， 所有队列中的命令才会被执行。另一方面， 通过调用 [DISCARD](http://redis.cn/commands/discard.html) ， 客户端可以清空事务队列， 并放弃执行事务。当客户端处于事务状态时， 所有传入的命令都会返回一个内容为 `QUEUED` 的状态回复（status reply）， 这些被入队的命令将在 EXEC 命令被调用时执行。[EXEC](http://redis.cn/commands/exec.html) 命令的回复是一个数组， 回复元素的先后顺序和命令发送的先后顺序一致。

**事务中的错误**

使用事务时可能会遇上以下两种错误：

- 事务在执行 [EXEC](http://redis.cn/commands/exec.html) 之前，入队的命令可能会出错。比如说，命令可能会产生语法错误（参数数量错误，参数名错误，等等），或者其他更严重的错误，比如内存不足（如果服务器使用 `maxmemory` 设置了最大内存限制的话）。
- 命令可能在 [EXEC](http://redis.cn/commands/exec.html) 调用之后失败。举个例子，事务中的命令可能处理了错误类型的键，比如将列表命令用在了字符串键上面，诸如此类。

对于发生在 [EXEC](http://redis.cn/commands/exec.html) 执行之前的错误，当命令在入队时产生错误， 错误会立即被返回给客户端，服务器会在客户端调用 [EXEC](http://redis.cn/commands/exec.html) 命令时，拒绝执行并自动放弃这个事务。

至于那些在 [EXEC](http://redis.cn/commands/exec.html) 命令执行之后所产生的错误， 事务中的其他命令仍然会继续执行。

为什么不支持回滚：

Redis的作者在事务文档中是这样解释的：不支持回滚是因为这种复杂的功能和Redis追求的简单高效的设计主旨不相符，Redis 命令只会因为错误的语法而失败（并且这些问题不能在入队时发现），或是命令用在了错误类型的键上面：这也就是说，失败的命令是由编程错误造成的，而这些错误应该在开发的过程中被发现，而不应该出现在生产环境中，所以没必要为Redis开发回滚功能。

**使用 check-and-set 操作实现乐观锁**

[WATCH](http://redis.cn/commands/watch.html) 命令可以为 Redis 事务提供 check-and-set （CAS）行为。

被 [WATCH](http://redis.cn/commands/watch.html) 的键会被监视，并会发觉这些键是否被改动过了。 如果有至少一个被监视的键在` EXEC 执行之前`被修改了， 那么整个事务都会被`取消`， [EXEC](http://redis.cn/commands/exec.html) 返回[nil-reply](http://redis.cn/topics/protocol.html#nil-reply)来表示事务已经失败。

# 统计数据

## UV【Unique Visiter】：独立访客

> 独立访客最好不要根据ip统计，因为相同的IP可能有不同的客户端。
>
> 改进：uv的统计依据是cookie，统计的是设备数，准确说是浏览器的数量，如果一台电脑装了3个浏览器，分别打开同一个页面，将会产生3个UV。
>
> 具体实现是当访客第一次访问网站时，服务器在浏览器端放入一个作为访问标识的cookie，可以为每一位访客设置访客Id放入cookie,该cookie的过期时间是第二天的零点，可以通过`response.setHandler("Set-Cookie"，xxx)`设置。
>
> 当天再次发起请求时拦截器发现已经有了该cookie就不会再次统计，所以其实每日UV已经自动去重过了，将每天的UV数据放入`HyperLoglog`是为了统计`区间UV`时进行数据合并的方便。
>
> 如果不统计区间UV其实直接使用int类型就可以了。
>
> 思路：
>
> 传两条cookie:
>
> 1. 访客Id，永远不会过期
> 2. 当天过期的cookie 
>
> 两个cookie的必要性：因为要统计区间UV，利用`HyperLoglog`，每一个客户端都需要有一个唯一的身份标识，不能改变，所以要有一个长期有效的cookie存储访客id作为身份标识，另外单日UV的统计需要当天的访问数据，所以当天的访问也需要一个cookie记录，并且过期时间是第二天的零点。
>
> 可以使用**拦截器**实现。
>
> 换成了根据cookie实现，通过设置在下一天的零点过期，记录当日的访问情况，计入HyperLoglog，区间的HyperLogLog由每日的记录合并而成。
>
> 拦截器 
>
> 所以一般情况下，UV大于ip,但是也不一定，比如家庭宽带拨号用户，第一次连接分配一次IP，断开后再连接，又重新分配一个IP，就会出现UV小于IP的情况。

### HyperLoglog

1. 统计基数

2. 占据空间小，无论统计多少数据，只占`12KB`的空间,因为 HyperLogLog` 只会根据输入元素来计算基数`，而`不会储存输入元素本身`，所以 HyperLogLog 不能像集合那样，返回输入的各个元素。

3. `不精确的统计算法`，标准误差为`0.18%`

4. `HyperLoglog`的命令集合：

   - `pfadd key element`：将指定元素加入HyperLoglog
   - `pfcount key`:统计HyperLoglog的基数值
   - `pfmerge desKey key1 key2...`:合并多个HyperLoglog，并将其保存在destKey中。

   合并操作对应`redisTemplate.opsForHyperLogLog().union(redisKey, keyList.toArray());`

==实现：==

- 单日UV：key是记录当天的时间，value是`HyperLoglog`类型的，记录的是`基数`，并不是元素集合，

- 区间UV：合并区间内所有日期的UV
- 每一天都有一条UV数据，计算区间UV时，把区间UV的key集合进行合并，在spring中对应`redisTemplate.opsForHyperLogLog().union(...)`操作，redis命令对应`pfmerge(...)`

==**DAU和UV的区别**==

- UV统计的是设备数，浏览器数，DAU统计的是注册用户。
- 同一台设备可以登录不同的用户，DAU就对应多条记录，而多个用户在同一台设备上登录只算一条UV数据。
- UV不一定登录，DAU需要登录

## DAU【Daily Active User】：日活跃用户

使用`bitmap`类型进行统计。

### bitMap

- 可以看做是一个位数组，每个userId占一位，如果当天用户访问了就在这个位置记为1，否则就是0。

- bitMap在Redis中使用字符串存储，最多可以存储512MB的数据，2^32位，可以存储42亿用户的数据，容量是足够的。
- 设置时候时间复杂度O(1)、读取时候时间复杂度O(n)，操作是非常快。

==实现==

- 单日DAU：`dau:date-->bitmap`
- 区间DAU：`dau:startDate:endDate——>bitmap`，使用OR运算统计日期内的日活跃用户
- 每一天都有一条DAU数据，统计区间DAU时，使用OR运算

```java
//统计时间范围内的日活跃用户，只要某一天属于日活跃用户，就要统计在内。
        // 进行OR运算
        return (long) redisTemplate.execute(new RedisCallback() {
            //redisTemplate直接调用opsfor..来操作redis数据库，每执行一条命令是要重新拿一个连接，因此很耗资源，让一个连接直接执行多条语句的方法就是使用SessionCallback，同样作用的还有RedisCallback。
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                String redisKey = RedisKeyUtil.getDAUKey(df.format(start), df.format(end));
                connection.bitOp(RedisStringCommands.BitOperation.OR,
                        redisKey.getBytes(), keyList.toArray(new byte[0][0]));
                return connection.bitCount(redisKey.getBytes());
            }
        });
```

# 定时任务

**定时任务的实现方式**：

1. JDK的线程池：`ExecutorService`、`ScheduledExecutorService`

    - ```java
     scheduledExecutorService.scheduleAtFixedRate(task, 10000, 1000, TimeUnit.MILLISECONDS);
     ```

2. Spring线程池：`ThreadPoolTaskExector`、`ThreadPoolTaskScheduler`

3. 分布式定时任务：`Spring Quartz`，分布式部署下，Quartz可以解决数据不共享的问题，Quartz的定时任务的信息存储在数据库中，分布式部署下可以共享数据。

####  Quartz

定时任务的信息存在数据库中，默认提供11张表，用于存储定时任务和调度信息。

1.1.qrtz_blob_triggers : 以Blob 类型存储的触发器。
1.2.qrtz_calendars：存放日历信息， quartz可配置一个日历来指定一个时间范围。
1.3.qrtz_cron_triggers：存放cron类型的触发器。
1.4.qrtz_fired_triggers：存放已触发的触发器。

1.6.qrtz_job_listeners：job**监听器**。
1.7.qrtz_locks： 存储程序的悲观锁的信息(假如使用了悲观锁)。
1.8.qrtz_paused_trigger_graps：存放暂停掉的触发器。
1.9.qrtz_scheduler_state：调度器状态。
1.10.qrtz_simple_triggers：简单触发器的信息。
1.11.qrtz_trigger_listeners：触发器监听器。
1.12.qrtz_triggers：触发器的基本信息。

Quartz的核心接口：`Scheduler`和`Job`。`JobDetail`用来配置Job,`Trigger`用来配置Job以什么样的频率来运行。

> 使用Quartz的步骤：通过`Job`接口定义一个任务，然后通过`JobDetail`和`Trigger`接口配置这个job的必要信息以及执行频率，配置存储方式是`JDBC`,之后程序启动的时候`Quartz`就会读取配置的信息，并且把读到的信息存入数据库中，之后就直接从数据库中读取配置来执行任务。数据库中的任务信息可以通过deleteJob删除。

数据库中主要的表：

1. `qrtz_job_details`：存放一个jobDetail信息。包括：job的名字、分组、描述等信息
2. `qrtz_triggers`:存放触发器的基本信息，包括调度器的名字，触发器名字、分组，Job的名字、分组，下一次触发的时刻等信息。
3. ,`qrtz_simple_trggers`：简单触发器的信息。
4. `qrtz_scheduler_state`：调度器状态。
5. `qrtz_locks`： 存储程序的悲观锁的信息(假如使用了悲观锁)。

## 使用`Quartz`计算帖子分数

1. 定义任务

   - 继承`Job`接口，重写`execute`方法
     - **计算帖子分数：**查看缓存里面是否有执行过影响分数操作的帖子，这些帖子在Redis中是以set存储的，当有点赞、评论操作时就会把这些帖子放进Redis中，定时任务到了，就把帖子从set中pop出来，更新分数就行。

2. 配置JobDetail的必要信息，

   ```java
   //配置JobDetail
   @Bean
   public JobDetailFactoryBean postScoreRefreshJobDetail() {
       JobDetailFactoryBean factoryBean = new JobDetailFactoryBean();
       factoryBean.setJobClass(PostScoreRefreshJob.class);//
       factoryBean.setName("postScoreRefreshJob");
   
       factoryBean.setGroup("communityJobGroup");
       factoryBean.setDurability(true);
       factoryBean.setRequestsRecovery(true);
       return factoryBean;
   }
   ```

3. 配置Trigger,执行频率

   ```java
   @Bean
   public SimpleTriggerFactoryBean postScoreRefreshTrigger(JobDetail postScoreRefreshJobDetail) {
       SimpleTriggerFactoryBean factoryBean = new SimpleTriggerFactoryBean();
       factoryBean.setJobDetail(postScoreRefreshJobDetail);
       factoryBean.setName("postScoreRefreshTrigger");
       factoryBean.setGroup("communityTriggerGroup");
       factoryBean.setRepeatInterval(1000 * 60 * 5);//5分钟
       factoryBean.setJobDataMap(new JobDataMap());
       return factoryBean;
   }
   ```

### 帖子排行

两种模式：按照分数排,按照创建时间排。使用`if test标签`

```xml
<select id="selectDiscussPosts" resultType="DiscussPost">
        select <include refid="selectFields"></include>
        from discuss_post
        where status != 2/*没有删除*/
        <if test="userId!=0">
            and user_id = #{userId}
        </if>
        <if test="orderMode==0">/*按照创建时间排序，type=1置顶，type=0普通*/
            order by type desc, create_time desc
        </if>
        <if test="orderMode==1">/*按照分数排序*/
            order by type desc, score desc, create_time desc
        </if>
        limit #{offset}, #{limit}
    </select>
```


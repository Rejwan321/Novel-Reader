//Search bar
$(document).ready(function(){

    $(".fa-search").click(function(){
        $(".search-bar, .input").toggleClass("active");
        $(".input[type='text']").focus();
    });

});

//show and hide login
const showPopupBtn = document.querySelector(".log");

showPopupBtn.addEventListener("click",() =>{
    document.body.classList.toggle("show-popup");
});

document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
        showPopupBtn.click();
    }
});

//sliding login funtion
const signUp = document.getElementById('signup');
const login = document.getElementById('login');
const container = document.getElementById('container');

signUp.addEventListener('click', () => {
    container.classList.add('right-panel-active');
})
login.addEventListener('click', () => {
    container.classList.remove('right-panel-active');
});

(function () {
  // Mark active nav link based on path
  const path = window.location.pathname;
  const links = document.querySelectorAll('a[data-nav]');
  links.forEach(a => {
    const match = a.getAttribute('href');
    if (match && path.startsWith(match)) {
      a.classList.add('active');
    }
  });

  // Scroll to top button example (if you want)
  const toTop = document.querySelector('#toTop');
  if (toTop) {
    toTop.addEventListener('click', () => window.scrollTo({ top: 0, behavior: 'smooth' }));
  }
})();
